package com.ml.mati

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.ImageFormat
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.ml.custom.FirebaseCustomLocalModel
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.ml.mati.ui.theme.MatiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        println("CRB before local model")
        val localModel = LocalModel.Builder()
            .setAssetFilePath("cnn_small_quantized.tflite").build()
        // or .setAbsoluteFilePath(absolute file path to model file)
        // or .setUri(URI to model file)
        println("CRB after local model")

        setContent {
            MatiTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GalleryButton(localModel)
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Composable
private fun GalleryButton(
    localModel: LocalModel,
) = Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.BottomCenter,
) {
    val bitmap = remember { mutableStateOf<Bitmap?>(null) }
    val string = remember { mutableStateOf("") }
    val context = LocalContext.current

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
            CoroutineScope(Dispatchers.IO).launch {
                if (it == null) {
                    string.value = "No photo"
                } else {
                    if (Build.VERSION.SDK_INT < 28) {
                        val galleryBitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                        bitmap.value = galleryBitmap.scale2()
                    } else {
                        val source = ImageDecoder.createSource(context.contentResolver, it)
                        val galleryBitmap = ImageDecoder.decodeBitmap(source)
                        bitmap.value = galleryBitmap.scale2()
                    }
                }
            }
        }
    if (bitmap.value != null) {
        println("CRB before image labeler options")
        val customImageLabelerOptions = CustomImageLabelerOptions.Builder(localModel)
            .setConfidenceThreshold(0.5f)
            .setMaxResultCount(5)
            .build()
        println("CRB before labeler")
        val imageLabeler = ImageLabeling.getClient(customImageLabelerOptions)
        println("CRB after labeler")
        //val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        val inputImage = InputImage.fromBitmap(bitmap.value!!, 0)

/*
            val inputImage = InputImage.fromByteBuffer(
                convertBitmapToByteBuffer(bitmap.value!!),
                96,
                96,
                0,
                ImageFormat.YV12)

 */
            println("CRB inputImage")
            imageLabeler
                .process(inputImage)
                .addOnSuccessListener {
                    println("CRB success ")
                    val imageLabel = it.maxBy { imageLabel -> imageLabel.confidence }
                    it.forEach { imageLabels -> println("CRB image label: ${imageLabels.text},${imageLabels.confidence},${imageLabels.index}") }

                    string.value =
                        "The image is:${imageLabel.text} - confidence:${imageLabel.confidence}"
                    imageLabeler.close()
                }
                .addOnFailureListener {
                    println("CRB fail $it")
                    imageLabeler.close()
                }


    }
    Column {
        Text(text = string.value)
        Button(
            onClick = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        ) {
            Text(text = "Galería")
        }
    }
}

private fun Bitmap.scale() = Bitmap.createScaledBitmap(this, 96, 96, true)
private fun Bitmap.scale2() = Bitmap.createBitmap(this, 0,0,96, 96)

private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
    val byteBuffer = ByteBuffer.allocateDirect(Float.SIZE_BYTES*96*96*3)
    byteBuffer.order(ByteOrder.nativeOrder())

    val pixels = IntArray(96 * 96)
    println("CRB antes del copy")
    val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    println("CRB despues del copy")
    newBitmap.getPixels(pixels, 0, newBitmap.width, 0, 0, newBitmap.width, newBitmap.height)
    println("CRB getPixels")
    var pixel = 0
    for (i in 0 until 96) {
        for (j in 0 until 96) {
            val pixelVal = pixels[pixel++]

            byteBuffer.putFloat(((pixelVal shr 16 and 0xFF)) / 255.0f)
            byteBuffer.putFloat(((pixelVal shr 8 and 0xFF) ) / 255.0f)
            byteBuffer.putFloat(((pixelVal and 0xFF)) / 255.0f)

        }
    }
    println("CRB recycle")
    newBitmap.recycle()
    println("CRB recycle 2")

    return byteBuffer
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MatiTheme {
        Greeting("Android")
    }
}