package cat.coeli.alerts.app.infrastructure

import cat.coeli.alerts.app.domain.Greeter
import cat.coeli.alerts.sample.cycle.a.A
import cat.coeli.alerts.sample.domain.Report
import cat.coeli.alerts.sample.infrastructure.OnlyNeedsTheDomain
import cat.coeli.alerts.sample.infrastructure.UsesAnUncataloguedLibrary

// Compiled into the `src/main` of the app `apps/app`.

object App {
    @JvmStatic
    fun main() {
        println(Report("Title").render())
        println(OnlyNeedsTheDomain().report().render() + UsesAnUncataloguedLibrary().checksum())
        println(A().b().a())
        println(Greeter().greet())
    }
}
