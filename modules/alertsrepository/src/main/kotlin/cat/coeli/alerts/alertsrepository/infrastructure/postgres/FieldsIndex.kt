package cat.coeli.alerts.alertsrepository.infrastructure.postgres

import org.jetbrains.exposed.v1.core.Expression

/**
 * Position of each column in a hand-written SELECT, as `ResultRow.create` expects it: pass the
 * columns in the same order as they appear in the SELECT.
 */
internal fun fieldsIndexOf(vararg columns: Expression<*>): Map<Expression<*>, Int> =
    columns.withIndex().associate { (index, column) -> column to index }
