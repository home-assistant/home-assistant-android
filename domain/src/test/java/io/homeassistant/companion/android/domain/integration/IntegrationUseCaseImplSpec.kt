package io.homeassistant.companion.android.domain.integration

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object IntegrationUseCaseImplSpec : Spek({
    describe("integration use case") {
        val deviceRegistration = DeviceRegistration(
            "appId",
            "appName",
            "appVersion",
            "deviceName",
            "manufacturer",
            "model",
            "osName",
            "osVersion",
            false,
            null
        )

        val integrationRepository by memoized {
            mockk<IntegrationRepository>(
                relaxed = true,
                relaxUnitFun = true
            )
        }
        val useCase by memoized { IntegrationUseCaseImpl(integrationRepository) }

        describe("registerDevice") {
            beforeEachTest {
                coEvery {
                    integrationRepository.registerDevice(any())
                } just Runs

                runBlocking {
                    useCase.registerDevice(deviceRegistration)
                }
            }

            it("should call repository") {
                coVerify {
                    integrationRepository.registerDevice(deviceRegistration)
                }
            }
        }

        describe("isRegistered") {
            beforeEachTest {
                runBlocking { useCase.isRegistered() }
            }

            it("should call the repository") {
                coVerify { integrationRepository.isRegistered() }
            }
        }
    }
})