package cat.coeli.alerts.alertsrepository.domain

class InMemoryAlertRepositoryTest : AlertRepositoryContractTest() {
    override fun newRepository(): AlertRepository = InMemoryAlertRepository()
}
