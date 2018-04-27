package ml.yuisyo.camera2test

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

class AutoFitTextureView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : TextureView(context, attrs, defStyle) {
    private var mRatioWidth: Int = 0
    private var mRatioHeight: Int = 0
    fun setAspectRatio(width: Int, height: Int) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Size cannot be negative")
        }
        mRatioWidth = width
        mRatioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var width: Int = MeasureSpec.getSize(widthMeasureSpec)
        var height: Int = MeasureSpec.getSize(heightMeasureSpec)
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height)
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth)
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height)
            }
        }
    }

    fun setPreviewSize(width: Int, height: Int) {
        setAspectRatio(width, height)
    }
    fun getPreviewWidth(): Int {
        return mRatioWidth
    }
    fun getPreviewHeight(): Int {
        return mRatioHeight
    }
}