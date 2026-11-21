package cat.coeli.alerts.sample.infrastructure

import cat.coeli.alerts.sample.domain.Report

class OnlyNeedsTheDomain {
    fun report(): Report = Report("pure")
}
