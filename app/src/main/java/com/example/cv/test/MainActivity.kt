package com.example.cv.test

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import ru.sberdevices.common.logger.AndroidLoggerDelegate
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.cv.detection.CvApi
import ru.sberdevices.cv.detection.CvApiFactory
import ru.sberdevices.cv.detection.CvApiFactoryImpl
import ru.sberdevices.cv.detection.entity.humans.HumansDetectionAspect

private const val PERMISSION_REQUEST_CODE = 1

class MainActivity : AppCompatActivity() {

    private val logger = Logger.get(javaClass.simpleName)

    private val scope = CoroutineScope(Dispatchers.IO)

    private var cvApi: CvApi? = null
    private var cvApiJob: Job? = null

    init {
        Logger.setDelegates(AndroidLoggerDelegate())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            createComputerVision()
        } else {
            val notGrantedPermissions = permissions
                .zip(grantResults.toList())
                .filter { (_, result) -> result != PackageManager.PERMISSION_GRANTED }
                .map { (permission, _) -> permission }
                .toTypedArray()
            requestPermissions(
                notGrantedPermissions,
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions(
            arrayOf(
                "android.permission.CAMERA",
                "ru.sberdevices.permission.COMPUTER_VISION_SENSITIVE"
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun createComputerVision() {
        val cvApiFactory: CvApiFactory = CvApiFactoryImpl(this)
        cvApi = cvApiFactory.get()
        val isCvAvailable = cvApi?.isAvailableOnDevice()
        logger.debug { "isCvAvailable = $isCvAvailable"}

        cvApiJob = scope.launch {
            val version = cvApi?.getVersion()
            val serviceInfo = cvApi?.getServiceInfo()
            logger.debug { "Version $version, service info $serviceInfo}"}
        }

        cvApi?.observeHumans(setOf(HumansDetectionAspect.Body.Landmarks.HomaNet))
            ?.onStart { logger.debug { "init humans job"} }
            ?.catch { logger.debug { "ERROR: $it" } }
            ?.onCompletion { logger.debug { "humans job completed"} }
            ?.onEach { logger.debug { "onEach humans job $it"} }
            ?.launchIn(lifecycleScope + Dispatchers.IO)
    }

    override fun onStop() {
        cvApiJob?.cancel()
        cvApi?.close()
        super.onStop()
    }
}