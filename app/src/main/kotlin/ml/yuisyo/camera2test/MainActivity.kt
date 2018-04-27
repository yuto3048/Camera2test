package ml.yuisyo.camera2test

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.media.ImageReader
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {
    private val TAG = MainActivity::class.java.simpleName
    private var mTextureView: AutoFitTextureView? = null
    private var mImageView: ImageView? = null
    private var mCamera2: Camera2StateMachine? = null
    private var mButton1: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mTextureView = findViewById(R.id.TextureView) as AutoFitTextureView?
        mImageView = findViewById(R.id.ImageView) as ImageView?
        mButton1 = findViewById(R.id.imageButton1) as ImageButton?
        mCamera2 = Camera2StateMachine()

        mButton1!!.setOnClickListener {
            v -> onClickShutter(v)
        }
    }

    override fun onResume() {
        super.onResume()
        mCamera2!!.open(this, mTextureView)
    }

    override fun onPause() {
        mCamera2!!.close()
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && mImageView!!.visibility == View.VISIBLE) {
            mTextureView!!.visibility = View.VISIBLE
            mImageView!!.visibility = View.INVISIBLE
            return false
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onClickShutter(view: View) {
        mCamera2!!.takePicture(object :ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(reader: ImageReader?) {
                val image: Image = reader!!.acquireLatestImage()
                var buffer: ByteBuffer = image.planes[0].buffer
                var bytes: ByteArray = ByteArray(buffer.remaining())
                buffer.get(bytes)
                var bitmap: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                image.close()

                mImageView!!.setImageBitmap(bitmap)
                mImageView!!.visibility = View.VISIBLE
                mTextureView!!.visibility = View.INVISIBLE
            }
        })
    }
}
