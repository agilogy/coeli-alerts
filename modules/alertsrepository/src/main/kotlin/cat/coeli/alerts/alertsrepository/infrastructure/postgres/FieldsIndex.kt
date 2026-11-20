package cat.coeli.alerts.alertsrepository.infrastructure.postgres

import org.jetbrains.exposed.v1.core.Expression

/** Pass the columns in the same order as they appear in the SELECT. */
internal fun fieldsIndexOf(vararg columns: Expression<*>): Map<Expression<*>, Int> =
    columns.withIndex().associate { (index, column) -> column to index }
