package ml.yuisyo.camera2test

import android.app.Activity
import android.content.Context
import android.graphics.Camera
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.net.NetworkInfo
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import java.util.*

import kotlin.collections.List
import kotlin.jvm.internal.Ref

class Camera2StateMachine {
    private val TAG: String = Camera2StateMachine::class.java.simpleName
    private var mCameraManager: CameraManager? = null

    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mImageReader: ImageReader? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null

    private var mTextureView: AutoFitTextureView? = null
    private var mHandler: Handler? = null
    private var mState: State? = null
    private var mTakePictureListener: ImageReader.OnImageAvailableListener? = null

    fun open(activity: Activity, textureView: AutoFitTextureView?) {
        if (mState != null) throw IllegalStateException("Already started state=" + mState)
        mTextureView = textureView
        mCameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        nextState(mInitSurfaceState)
    }
    fun takePicture(listener: ImageReader.OnImageAvailableListener) :Boolean {
        if (mState != mPreviewState) {
            return false
        }
        mTakePictureListener = listener
        nextState(mAutoFocusState)
        return true
    }
    fun close() {
        nextState(mAbortState)
    }

    private fun shutdown() {
        if (null != mCaptureSession) {
            mCaptureSession?.close()
            mCaptureSession = null
        }
        if (null != mCameraDevice) {
            mCameraDevice?.close()
            mCameraDevice = null
        }
        if (null != mImageReader) {
            mImageReader?.close()
            mImageReader = null
        }
    }

    private fun nextState(nextState: State?) {
        Log.d(TAG, "state: " + mState + "->" + nextState)
        try {
            if (mState != null) mState?.finish()
            mState = nextState
            if (mState != null) mState?.enter()
        } catch (e: CameraAccessException) {
            Log.d(TAG, "next(" + nextState + ")", e)
            shutdown()
        }
    }

    private abstract class State(private val mName: String) {
        override fun toString(): String { return mName }

        @Throws(CameraAccessException::class)
        open fun enter() {}
        open fun onSurfaceTextureAvailable(width: Int, height: Int) {}
        open fun onCameraOpened(cameraDevice: CameraDevice) {}
        open fun onSessionConfigured(cameraCaptureSession: CameraCaptureSession) {}
        @Throws(CameraAccessException::class)
        open fun onCaptureResult(result: CaptureResult, isCompleted: Boolean) {}
        @Throws(CameraAccessException::class)
        open fun finish() {}
    }

    private val mInitSurfaceState: State = object: State("InitSurface") {
        @Throws(CameraAccessException::class)
        override fun enter(){
            if (mTextureView!!.isAvailable()) {
                nextState(mOpenCameraState)
            } else {
                mTextureView!!.surfaceTextureListener = mSurfaceTextureListener
            }
        }
        override fun onSurfaceTextureAvailable(width: Int, height: Int) {
            nextState(mOpenCameraState)
        }

        private val mSurfaceTextureListener: TextureView.SurfaceTextureListener = object: TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                if (mState != null) (mState as State).onSurfaceTextureAvailable(width, height)
            }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                // TODO: rotation changed
            }
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                return true
            }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
        }
    }

    private val mOpenCameraState: State = object: State("OpenCamera") {
        @Throws(CameraAccessException::class)
        override fun enter() {
            //var cameraId: String? = Camera2Util.getCameraId(mCameraManager, CameraCharacteristics.LENS_FACING_BACK)
            var cameraId: String? = 0.toString() //in mi6 0: wide angle, 2: telephoto
            var characteristics: CameraCharacteristics = mCameraManager!!.getCameraCharacteristics(cameraId)
            var map: StreamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            mImageReader = Camera2Util.getMaxSizeImageReader(map, ImageFormat.JPEG)
            var previewSize: Size = Camera2Util.getBestPreviewSize(map, (mImageReader as ImageReader))
            mTextureView!!.setPreviewSize(previewSize.height, previewSize.width)

            (mCameraManager as CameraManager).openCamera(cameraId, mStateCallback, mHandler)
            Log.d(TAG, "openCamera" + cameraId)
        }

        override fun onCameraOpened(cameraDevice: CameraDevice) {
            mCameraDevice = cameraDevice
            nextState(mCreateSessionState)
        }

        private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(cameraDevice: CameraDevice) {
                if (mState != null) (mState as State).onCameraOpened(cameraDevice)
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {
                nextState(mAbortState)
            }

            override fun onError(cameraDevice: CameraDevice, error: Int) {
                Log.e(TAG, "CameraDevice:onError" + error)
                nextState(mAbortState)
            }
        }
    }

    private val mCreateSessionState = object: State("CameraSession") {
        @Throws(CameraAccessException::class)
        override fun enter() {
            mPreviewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            var texture: SurfaceTexture = mTextureView!!.surfaceTexture
            texture.setDefaultBufferSize((mTextureView as AutoFitTextureView).getPreviewWidth(), (mTextureView as AutoFitTextureView).getPreviewHeight())
            var surface: Surface = Surface(texture)
            (mPreviewRequestBuilder as CaptureRequest.Builder).addTarget(surface)
            var outputs: List<Surface> = Arrays.asList(surface, mImageReader!!.surface)
            (mCameraDevice as CameraDevice).createCaptureSession(outputs, mSessionCallback, mHandler)
        }
        override fun onSessionConfigured(cameraCaptureSession: CameraCaptureSession) {
            mCaptureSession = cameraCaptureSession
            nextState(mPreviewState)
        }

        private val mSessionCallback: CameraCaptureSession.StateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                if (mState != null) (mState as State).onSessionConfigured(cameraCaptureSession)
            }

            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                nextState(mAbortState)
            }
        }
    }

    private val mPreviewState = object : State("Preview") {
        @Throws(CameraAccessException::class)
        override fun enter() {
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            mCaptureSession!!.setRepeatingRequest((mPreviewRequestBuilder as CaptureRequest.Builder).build(), mCaptureCallback, mHandler)
        }
    }
    private val mCaptureCallback: CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest, partialResult: CaptureResult) {
            onCaptureResult(partialResult, false)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            onCaptureResult(result, true)
        }
        fun onCaptureResult(result: CaptureResult, isCompleted: Boolean) {
            try {
                if (mState != null) (mState as State).onCaptureResult(result, isCompleted)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "handle():", e)
                nextState(mAbortState)
            }
        }
    }

    private val mAutoFocusState = object : State("AutoFocus") {
        @Throws(CameraAccessException::class)
        override fun enter() {
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            mCaptureSession!!.setRepeatingRequest((mPreviewRequestBuilder as CaptureRequest.Builder).build(), mCaptureCallback, mHandler)
        }
        @Throws(CameraAccessException::class)
        override fun onCaptureResult(result: CaptureResult, isCompleted: Boolean) {
            var afState: Int? = result.get(CaptureResult.CONTROL_AF_STATE)
            var isAfReady: Boolean = afState == null || afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED

            if (isAfReady) {
                nextState(mAutoExposureState)
            }
        }
    }

    private val mAutoExposureState = object : State("AutoExposure") {
        @Throws(CameraAccessException::class)
        override fun enter() {
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            mCaptureSession!!.setRepeatingRequest((mPreviewRequestBuilder as CaptureRequest.Builder).build(), mCaptureCallback, mHandler)
        }

        @Throws(CameraAccessException::class)
        override fun onCaptureResult(result: CaptureResult, isCompleted: Boolean) {
            var aeState: Int? = result.get(CaptureResult.CONTROL_AE_STATE)
            var isAeReady: Boolean = aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED || aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED

            if (isAeReady) {
                nextState(mTakePictureState)
            }
        }
    }

    private val mTakePictureState = object : State("TakePicture") {
        @Throws(CameraAccessException::class)
        override fun enter() {
            val captureBuilder: CaptureRequest.Builder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(mImageReader!!.surface)
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90)
            mImageReader!!.setOnImageAvailableListener(mTakePictureListener, mHandler)

            mCaptureSession!!.stopRepeating()
            mCaptureSession!!.capture(captureBuilder.build(), mCaptureCallback, mHandler)
        }

        @Throws(CameraAccessException::class)
        override fun onCaptureResult(result: CaptureResult, isCompleted: Boolean) {
            if (isCompleted) {
                nextState(mPreviewState)
            }
        }

        @Throws(CameraAccessException::class)
        override fun finish() {
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            mCaptureSession!!.capture((mPreviewRequestBuilder as CaptureRequest.Builder).build(), mCaptureCallback, mHandler)
            mTakePictureListener = null
        }
    }

    private val mAbortState = object :State("Abort") {
        @Throws(CameraAccessException::class)
        override fun enter() {
            shutdown()
            nextState(null)
        }
    }
}