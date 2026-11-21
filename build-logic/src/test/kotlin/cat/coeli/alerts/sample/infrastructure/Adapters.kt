package cat.coeli.alerts.sample.infrastructure

import cat.coeli.alerts.sample.domain.readsAFile
import cat.coeli.alerts.sample.domain.usedInModule
import java.util.zip.CRC32

class UsesAnUncataloguedLibrary {
    fun checksum(): Long = CRC32().value + readsAFile("x").length + usedInModule()
}
