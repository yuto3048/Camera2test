package ml.yuisyo.camera2test

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.util.Size

open class Camera2Util {
    companion object {
        @Throws(CameraAccessException::class)
        fun getCameraId(cameraManager: CameraManager?, facing: Int): String? {
            cameraManager!!.cameraIdList.forEach {
                val characteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(it)
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == facing) {
                    return it
                }
            }
            return null
        }

        @Throws(CameraAccessException::class)
        public fun getMaxSizeImageReader(map: StreamConfigurationMap, imageFormat: Int): ImageReader {
            var sizes: Array<Size> = map.getOutputSizes(imageFormat)
            var maxSize: Size = sizes[0]
            sizes.forEach {
                if (it.width > maxSize.width) {
                    maxSize = it
                }
            }
            var imageReader: ImageReader = ImageReader.newInstance(
                    //maxSize.width, maxSize.height, // for landscape.
                    maxSize.height, maxSize.width, // for portrait.
                    imageFormat, 1)
            return imageReader
        }

        @Throws(CameraAccessException::class)
        fun getBestPreviewSize(map: StreamConfigurationMap, imageSize: ImageReader): Size {
            //var imageAspect: Float? = (imageSize.width as Float / imageSize.height) as? Float // for landscape.
            var imageAspect: Float = imageSize.height.toFloat() / imageSize.width.toFloat()
            var minDiff: Float = 1000000000000F
            var previewSizes: Array<Size> = map.getOutputSizes(SurfaceTexture::class.java)
            var previewSize: Size = previewSizes[0]
            run loop@ {
                previewSizes.forEach {
                    var previewAspect: Float = it.width.toFloat() / it.height.toFloat()
                    var diff: Float = Math.abs(imageAspect - previewAspect)
                    if (diff < minDiff) {
                        previewSize = it
                        minDiff = diff
                    }
                    if (diff == 0.0F) {
                        return@loop
                    }
                }
            }
            return previewSize
        }
    }
}