package cat.coeli.alerts.architecturecheck.infrastructure

import cat.coeli.alerts.architecturecheck.domain.Catalogue
import cat.coeli.alerts.architecturecheck.domain.Finding
import cat.coeli.alerts.architecturecheck.domain.FindingKind
import cat.coeli.alerts.architecturecheck.domain.SourceLine
import cat.coeli.alerts.architecturecheck.domain.UseScope
import cat.coeli.alerts.architecturecheck.domain.Verdict
import cat.coeli.alerts.architecturecheck.domain.Visibility
import cat.coeli.alerts.architecturecheck.domain.verdictOf
import com.tngtech.archunit.core.domain.JavaAnnotation
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaCodeUnit
import com.tngtech.archunit.core.domain.JavaMember
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.domain.SourceCodeLocation
import kotlin.metadata.ClassKind
import kotlin.metadata.KmClass
import kotlin.metadata.KmDeclarationContainer
import kotlin.metadata.KmFunction
import kotlin.metadata.KmProperty
import kotlin.metadata.MemberKind
import kotlin.metadata.Modality
import kotlin.metadata.Visibility as KmVisibility
import kotlin.metadata.isConst
import kotlin.metadata.isData
import kotlin.metadata.isInline
import kotlin.metadata.isSecondary
import kotlin.metadata.isValue
import kotlin.metadata.jvm.JvmMethodSignature
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.Metadata
import kotlin.metadata.jvm.fieldSignature
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.setterSignature
import kotlin.metadata.jvm.signature
import kotlin.metadata.kind
import kotlin.metadata.modality
import kotlin.metadata.visibility

/**
 * A function, property or constructor as Kotlin declares it, with the bytecode members it compiled
 * to ([handles]): a use of any of them is a use of the declaration. [visibility] is the effective
 * one, narrowed by the classes around.
 */
private class Declaration(
    val identity: String,
    val owner: JavaClass,
    val placement: Placement,
    val visibility: Visibility,
    val canBePrivate: Boolean,
    /** Not judged, although its uses still count for what it overrides. */
    val isExempt: Boolean,
    val handles: Set<JavaMember>,
    val reachesPrivately: (JavaClass) -> Boolean,
)

/**
 * The members of `src/main` and `src/testFixtures` whose uses, from any source set, need less
 * visibility or none.
 */
internal fun memberFindingsOf(
    placements: Map<JavaClass, Placement>,
    modules: List<Module>,
    catalogue: Catalogue,
    sourceLineOf: (JavaClass, Placement, SourceCodeLocation) -> SourceLine?,
): List<Finding> {
    val metadata = placements.keys.associateWith { it.kotlinMetadata() }
    val declarations =
        placements
            .filterValues { it.sourceSet != SourceSet.Test }
            .flatMap { (javaClass, placement) ->
                Declarations(javaClass, placement, metadata).all()
            }
    val byHandle =
        declarations
            .flatMap { declaration -> declaration.handles.map { it to declaration } }
            .toMap()
    val apps = modules.filter { it.isApp }.map { it.path }.toSet()
    val usesByRoot =
        declarations
            .flatMap { declaration ->
                val uses = usesOf(declaration, placements, catalogue)
                rootsOf(declaration, byHandle).map { it to uses }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, uses) -> uses.flatten().toSet() }
    val overridersByRoot =
        declarations
            .filter { it.overridden().isNotEmpty() }
            .flatMap { override ->
                rootsOf(override, byHandle).map { root ->
                    root to
                        if (override.placement.module == root.placement.module) UseScope.Module
                        else UseScope.OtherModule
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, scopes) -> scopes.toSet() }
    return declarations
        .filter { !it.isExempt && it.overridden().isEmpty() }
        .mapNotNull { declaration ->
            verdictOf(
                    declaration.visibility,
                    usesByRoot[declaration].orEmpty(),
                    overridersByRoot[declaration].orEmpty(),
                    declaration.canBePrivate,
                    isApp = declaration.placement.module in apps,
                )
                ?.let { verdict ->
                    val source =
                        declaration.handles
                            .filterIsInstance<JavaCodeUnit>()
                            .firstOrNull { it.sourceCodeLocation.lineNumber > 0 }
                            ?.let {
                                sourceLineOf(
                                    declaration.owner,
                                    declaration.placement,
                                    it.sourceCodeLocation,
                                )
                            }
                    when (verdict) {
                        is Verdict.TooVisible ->
                            Finding(
                                FindingKind.MembersTooVisible,
                                declaration.placement.module,
                                declaration.identity,
                                "${verdict.current.keyword} → ${verdict.needed.keyword}",
                                source,
                            )
                        is Verdict.Unused ->
                            Finding(
                                FindingKind.UnusedMembers,
                                declaration.placement.module,
                                declaration.identity,
                                if (verdict.onlyTestsOfOtherModules)
                                    "used only by tests of other modules"
                                else null,
                                source,
                            )
                    }
                }
        }
}

/** Its own bytecode members calling each other, as a `$default` calls the function, don't count. */
private fun usesOf(
    declaration: Declaration,
    placements: Map<JavaClass, Placement>,
    catalogue: Catalogue,
): Set<UseScope> =
    declaration.handles
        .flatMap { it.accessesToSelf }
        .filterNot { it.origin in declaration.handles }
        .map { access ->
            val origin = access.originOwner
            val placement = placements[origin]
            when {
                placement == null -> UseScope.OtherModule
                placement.module != declaration.placement.module ->
                    if (
                        placement.sourceSet == SourceSet.Test &&
                            declaration.placement.sourceSet == SourceSet.Main &&
                            declaration.placement.module !in catalogue.testSupportModules
                    )
                        UseScope.OtherModuleTest
                    else UseScope.OtherModule
                placement == declaration.placement && declaration.reachesPrivately(origin) ->
                    UseScope.Declaration
                else -> UseScope.Module
            }
        }
        .toSet()

private fun Declaration.overridden(): List<JavaMethod> =
    handles.filterIsInstance<JavaMethod>().flatMap { it.overridden() }

/**
 * The declarations an override stands for: those at the top of what it overrides. One overriding a
 * library's member stands for none, so its uses count for nothing we judge.
 */
private fun rootsOf(
    declaration: Declaration,
    byHandle: Map<JavaMember, Declaration>,
): Set<Declaration> =
    declaration
        .overridden()
        .map { byHandle[it] }
        .let { overridden ->
            when {
                overridden.isEmpty() -> setOf(declaration)
                overridden.any { it == null } -> emptySet()
                else -> overridden.filterNotNull().flatMap { rootsOf(it, byHandle) }.toSet()
            }
        }

/**
 * By name and number of parameters, as generics erase differently on each side: a false match only
 * spares a member.
 */
private fun JavaMethod.overridden(): List<JavaMethod> =
    if (JavaModifier.STATIC in modifiers || JavaModifier.PRIVATE in modifiers) emptyList()
    else
        (owner.allRawSuperclasses + owner.allRawInterfaces).flatMap { supertype ->
            supertype.methods.filter {
                it.name == name &&
                    it.rawParameterTypes.size == rawParameterTypes.size &&
                    JavaModifier.PRIVATE !in it.modifiers &&
                    JavaModifier.STATIC !in it.modifiers
            }
        }

/** The declarations of one class file, as its Kotlin metadata lists them. */
private class Declarations(
    private val owner: JavaClass,
    private val placement: Placement,
    private val metadata: Map<JavaClass, KotlinClassMetadata?>,
) {
    private val methods = owner.methods.associateBy { it.name + it.descriptor }

    fun all(): List<Declaration> =
        when (val classMetadata = metadata[owner]) {
            is KotlinClassMetadata.Class -> ofClass(classMetadata.kmClass)
            is KotlinClassMetadata.FileFacade -> ofFile(classMetadata.kmPackage)
            is KotlinClassMetadata.MultiFileClassPart -> ofFile(classMetadata.kmPackage)
            else -> emptyList()
        }

    private fun ofFile(container: KmDeclarationContainer): List<Declaration> =
        membersOf(container, Visibility.Public, kmClass = null, fieldOwners = listOf(owner)) {
            origin ->
            origin.packageName == owner.packageName &&
                origin.sourceFileName() == owner.sourceFileName()
        }

    /**
     * An annotation has nothing to narrow, and what the serialization plugin generates is used by
     * reflection.
     */
    private fun ofClass(kmClass: KmClass): List<Declaration> {
        val visibility = effectiveVisibility()
        val skipped =
            kmClass.kind == ClassKind.ANNOTATION_CLASS ||
                kmClass.kind == ClassKind.ENUM_ENTRY ||
                owner.name.endsWith("\$\$serializer")
        return if (visibility == null || skipped) emptyList()
        else {
            val enclosing = owner.enclosingClass.orElse(null)
            val scope =
                if (kmClass.kind == ClassKind.COMPANION_OBJECT && enclosing != null) enclosing
                else owner
            val reachesPrivately = { origin: JavaClass ->
                generateSequence(origin) { it.enclosingClass.orElse(null) }.any { it == scope }
            }
            membersOf(
                kmClass,
                visibility,
                kmClass,
                listOfNotNull(owner, enclosing),
                reachesPrivately,
            ) + constructorsOf(kmClass, visibility, reachesPrivately)
        }
    }

    /**
     * The narrowest of the class's visibility and those around it; `null` for a local class or one
     * Kotlin didn't make.
     */
    private fun effectiveVisibility(): Visibility? =
        generateSequence(owner) { it.enclosingClass.orElse(null) }
            .map { javaClass ->
                (metadata[javaClass] as? KotlinClassMetadata.Class)
                    ?.kmClass
                    ?.visibility
                    ?.toVisibility()
            }
            .fold<Visibility?, Visibility?>(Visibility.Public) { narrowest, visibility ->
                if (narrowest == null || visibility == null) null else minOf(narrowest, visibility)
            }

    private fun membersOf(
        container: KmDeclarationContainer,
        visibility: Visibility,
        kmClass: KmClass?,
        fieldOwners: List<JavaClass>,
        reachesPrivately: (JavaClass) -> Boolean,
    ): List<Declaration> =
        container.functions
            .filter { it.kind == MemberKind.DECLARATION }
            .mapNotNull { functionOf(it, kmClass, visibility, reachesPrivately) } +
            container.properties
                .filter { it.kind == MemberKind.DECLARATION }
                .mapNotNull { propertyOf(it, kmClass, visibility, fieldOwners, reachesPrivately) }

    private fun functionOf(
        function: KmFunction,
        kmClass: KmClass?,
        classVisibility: Visibility,
        reachesPrivately: (JavaClass) -> Boolean,
    ): Declaration? {
        val method = function.signature?.let { methods[it.toString()] }
        return declaration(
            identity = "${owner.name}.${function.name}${parametersOf(method)}",
            visibility = function.visibility,
            classVisibility = classVisibility,
            canBePrivate = function.modality == Modality.FINAL,
            isExempt =
                function.name == "main" ||
                    function.isInline ||
                    '$' in function.name ||
                    isSerializerFactory(function, kmClass),
            handles = listOfNotNull(method) + defaultsOf(function.signature),
            reachesPrivately = reachesPrivately,
        )
    }

    /** The `serializer()` the serialization plugin adds to a companion is found by reflection. */
    private fun isSerializerFactory(function: KmFunction, kmClass: KmClass?): Boolean =
        kmClass?.kind == ClassKind.COMPANION_OBJECT && function.name == "serializer"

    private fun propertyOf(
        property: KmProperty,
        kmClass: KmClass?,
        classVisibility: Visibility,
        fieldOwners: List<JavaClass>,
        reachesPrivately: (JavaClass) -> Boolean,
    ): Declaration? {
        val field =
            property.fieldSignature?.let { signature ->
                fieldOwners.firstNotNullOfOrNull { fieldOwner ->
                    fieldOwner.fields.firstOrNull { it.name == signature.name }
                }
            }
        val wrapsValueClass =
            kmClass?.isValue == true && property.name == kmClass.inlineClassUnderlyingPropertyName
        return declaration(
            identity = "${owner.name}.${property.name}",
            visibility = property.visibility,
            classVisibility = classVisibility,
            canBePrivate = property.modality == Modality.FINAL,
            isExempt = property.isConst || property.getter.isInline || wrapsValueClass,
            handles =
                listOfNotNull(
                    property.getterSignature?.let { methods[it.toString()] },
                    property.setterSignature?.let { methods[it.toString()] },
                    field,
                ) + componentsOf(property, kmClass),
            reachesPrivately = reachesPrivately,
        )
    }

    /**
     * A value class's constructors compile to static functions of their own, and a serializable
     * class's is called by the code the plugin generates: neither is judged.
     */
    private fun constructorsOf(
        kmClass: KmClass,
        classVisibility: Visibility,
        reachesPrivately: (JavaClass) -> Boolean,
    ): List<Declaration> =
        if (kmClass.kind != ClassKind.CLASS || kmClass.isValue || owner.isSerializable())
            emptyList()
        else {
            val constructors = owner.constructors.associateBy { it.name + it.descriptor }
            kmClass.constructors.mapNotNull { constructor ->
                val jvmConstructor = constructor.signature?.let { constructors[it.toString()] }
                val copies =
                    if (kmClass.isData && !constructor.isSecondary)
                        owner.methods.filter { it.name == "copy" || it.name == "copy\$default" }
                    else emptyList()
                declaration(
                    identity = "${owner.name}${parametersOf(jvmConstructor)}",
                    visibility = constructor.visibility,
                    classVisibility = classVisibility,
                    canBePrivate = true,
                    isExempt = false,
                    handles =
                        listOfNotNull(jvmConstructor) +
                            constructorDefaultsOf(constructor.signature) +
                            copies,
                    reachesPrivately = reachesPrivately,
                )
            }
        }

    /** `protected` and local members are not judged. */
    private fun declaration(
        identity: String,
        visibility: KmVisibility,
        classVisibility: Visibility,
        canBePrivate: Boolean,
        isExempt: Boolean,
        handles: List<JavaMember>,
        reachesPrivately: (JavaClass) -> Boolean,
    ): Declaration? =
        visibility.toVisibility()?.let {
            Declaration(
                identity,
                owner,
                placement,
                minOf(it, classVisibility),
                canBePrivate,
                isExempt,
                handles.toSet(),
                reachesPrivately,
            )
        }

    /** What a call leaving out an argument with a default value calls instead. */
    private fun defaultsOf(signature: JvmMethodSignature?): List<JavaMethod> =
        signature
            ?.let { "${it.name}\$default" to it.parameters() }
            ?.let { (name, parameters) ->
                owner.methods.filter { it.name == name && parameters in it.descriptor }
            }
            .orEmpty()

    private fun constructorDefaultsOf(signature: JvmMethodSignature?): List<JavaMember> =
        signature
            ?.parameters()
            ?.let { parameters ->
                owner.constructors.filter {
                    parameters in it.descriptor &&
                        it.descriptor.endsWith("DefaultConstructorMarker;)V")
                }
            }
            .orEmpty()

    /** A data class's `componentN` reads the property its primary constructor declares at `N`. */
    private fun componentsOf(property: KmProperty, kmClass: KmClass?): List<JavaMethod> {
        val primary = kmClass?.takeIf { it.isData }?.constructors?.firstOrNull { !it.isSecondary }
        val position = primary?.valueParameters?.indexOfFirst { it.name == property.name } ?: -1
        return if (position < 0) emptyList()
        else
            owner.methods.filter {
                it.name == "component${position + 1}" && it.rawParameterTypes.isEmpty()
            }
    }
}

private fun JavaClass.isSerializable(): Boolean =
    isAnnotatedWith("kotlinx.serialization.Serializable")

/**
 * Its parameter types, receiver first, to tell overloads apart; `(?)` when the bytecode has no
 * member for it.
 */
private fun parametersOf(codeUnit: JavaCodeUnit?): String =
    codeUnit?.rawParameterTypes?.joinToString(prefix = "(", postfix = ")") { it.simpleName }
        ?: "(?)"

private fun JvmMethodSignature.parameters(): String =
    descriptor.substringAfter('(').substringBefore(')')

/** A `protected` class counts as public, since any subclass may reach it. */
private fun KmVisibility.toVisibility(): Visibility? =
    when (this) {
        KmVisibility.PRIVATE,
        KmVisibility.PRIVATE_TO_THIS -> Visibility.Private
        KmVisibility.INTERNAL -> Visibility.Internal
        KmVisibility.PUBLIC -> Visibility.Public
        KmVisibility.PROTECTED,
        KmVisibility.LOCAL -> null
    }

private fun JavaClass.sourceFileName(): String? = source.orElse(null)?.fileName?.orElse(null)

/** What the Kotlin compiler recorded about a class; `null` for one it didn't compile. */
private fun JavaClass.kotlinMetadata(): KotlinClassMetadata? =
    tryGetAnnotationOfType("kotlin.Metadata").orElse(null)?.let { annotation ->
        KotlinClassMetadata.readLenient(
            Metadata(
                kind = annotation.value("k") as Int?,
                metadataVersion = annotation.ints("mv"),
                data1 = annotation.strings("d1"),
                data2 = annotation.strings("d2"),
                extraString = annotation.value("xs") as String?,
                packageName = annotation.value("pn") as String?,
                extraInt = annotation.value("xi") as Int?,
            ),
        )
    }

private fun JavaAnnotation<*>.value(property: String): Any? = get(property).orElse(null)

private fun JavaAnnotation<*>.strings(property: String): Array<String>? =
    (value(property) as? Array<*>)?.map { it as String }?.toTypedArray()

private fun JavaAnnotation<*>.ints(property: String): IntArray? =
    when (val value = value(property)) {
        is IntArray -> value
        is Array<*> -> value.map { it as Int }.toIntArray()
        else -> null
    }
