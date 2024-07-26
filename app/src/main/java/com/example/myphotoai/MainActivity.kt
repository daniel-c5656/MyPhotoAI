package com.example.myphotoai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.ktor.client.*
import io.ktor.client.engine.android.*


import com.example.myphotoai.ui.theme.MyPhotoAITheme
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.jvm.Throws
import android.content.Context
import android.graphics.Paint.Align
import android.icu.text.DateFormat.getDateTimeInstance
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection.Companion.Content
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberAsyncImagePainter
import coil.compose.rememberImagePainter
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.ListContent
import com.aallam.openai.api.chat.TextContent
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.file.FileUpload
import com.aallam.openai.api.file.Purpose
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.Model
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.JdkConstants.HorizontalAlignment
import java.nio.file.Files
import java.util.Base64
import java.util.Locale
import java.util.Objects
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasRequiredPermissions())
        {
            ActivityCompat.requestPermissions(this, PERMISSIONS, 0)
        }
        val client = HttpClient(Android)

        // Define own keys in gradle.properties.
        val openAI = OpenAI(
            token = BuildConfig.OPENAI_TOKEN,
            organization = BuildConfig.OPENAI_ORG,
            timeout = Timeout(socket = 30.seconds)
        )
        val files = mutableListOf(createImageFile())
        var fileIdx = 0

        // enableEdgeToEdge()
        setContent {
            MyPhotoAITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->


                    Column(horizontalAlignment = Alignment.CenterHorizontally)
                    {
                        var result by remember { mutableStateOf("")}

                        Capture(aiRequest = {
                            val base64Image = toB64(files[fileIdx])
                            // Submits request asynchronously
                            lifecycleScope.launch {
                                val chatCompletionRequest = ChatCompletionRequest(
                                    // Use any OpenAI model capable of image upload.
                                    model = ModelId("gpt-4o"),
                                    messages = listOf(
                                        ChatMessage(role = ChatRole.System, content = "You are an image analyst."),
                                        ChatMessage(role = ChatRole.User, messageContent = ListContent(content =
                                            listOf(TextPart(text = "Please analyze this image."), ImagePart(url = "data:image/jpeg;base64,$base64Image"))
                                        ))),
                                    maxTokens = 1024
                                )
                                // Complete request and make a new file for another description.
                                val completion: ChatCompletion = openAI.chatCompletion(chatCompletionRequest)
                                result = completion.choices.map { choice -> choice.message.content }.joinToString(separator = ",")
                                files.add(createImageFile())
                                fileIdx++
                            }
                        }, files[fileIdx])

                        Text(result)
                    }

                }

            }
        }
    }
    private fun hasRequiredPermissions(): Boolean {
        return PERMISSIONS.all { ContextCompat.checkSelfPermission(applicationContext, it) == PackageManager.PERMISSION_GRANTED}
    }

    companion object
    {
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}



@OptIn(ExperimentalCoilApi::class)
@Composable
fun Capture(aiRequest: () -> Unit, file: File) {
    val context = LocalContext.current
    val uri = FileProvider.getUriForFile(
        Objects.requireNonNull(context),
        "com.example.myphotoai.provider",
        file
    )

    var capturedImageUri by remember {
        mutableStateOf<Uri>(Uri.EMPTY)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        capturedImageUri = uri;
        aiRequest()
    }

    Column (horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = {
                cameraLauncher.launch(uri)
            }
        ) {
            Text("Describe what's in front of me!")
        }
        if (capturedImageUri.path?.isNotEmpty() == true) {
            // Display the image on success.
            Image(
                modifier = Modifier.padding(16.dp, 8.dp),
                painter = rememberAsyncImagePainter(capturedImageUri),
                contentDescription = null
            )
        }
    }
}
fun Context.createImageFile(): File
{
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val imageFileName = "JPEG_" + timeStamp + "_"
    val image = File.createTempFile(imageFileName, ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))

    return image;
}

@OptIn(ExperimentalEncodingApi::class)
fun toB64(file: File): String
{
    val rawImg = file.readBytes()

    return kotlin.io.encoding.Base64.encode(rawImg)
}