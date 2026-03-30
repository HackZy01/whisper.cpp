package com.whispercppdemo.ui.main

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whispercppdemo.media.decodeWaveFile
import com.whispercppdemo.recorder.Recorder
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "MainScreenViewModel"

class MainScreenViewModel(private val application: Application) : ViewModel() {
    var canTranscribe by mutableStateOf(false)
        private set
    var dataLog by mutableStateOf("")
        private set
    var isRecording by mutableStateOf(false)
        private set

    private val modelsPath = File(application.filesDir, "models")
    private val samplesPath = File(application.filesDir, "samples")
    private var recorder: Recorder = Recorder()
    private var whisperContext: WhisperContext? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordedFile: File? = null

    init {
        viewModelScope.launch {
            printSystemInfo()
            loadData()
        }
    }

    private suspend fun printSystemInfo() {
        printMessage(String.format("System Info: %s\n", WhisperContext.getSystemInfo()))
    }

    private suspend fun loadData() {
        printMessage("Loading data...\n")
        try {
            copyAssets()
            loadBaseModel()
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }
    }

    private suspend fun printMessage(msg: String) = withContext(Dispatchers.Main) {
        dataLog += msg
    }

    private suspend fun copyAssets() = withContext(Dispatchers.IO) {
        modelsPath.mkdirs()
        samplesPath.mkdirs()
        application.copyData("samples", samplesPath, ::printMessage)
        printMessage("All base data copied to working directory.\n")
    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        printMessage("Loading base model...\n")
        val models = application.assets.list("models/")
        if (!models.isNullOrEmpty()) {
            whisperContext = WhisperContext.createContextFromAsset(application.assets, "models/" + models[0])
            printMessage("Loaded model ${models[0]}.\n")
            canTranscribe = true
        } else {
            printMessage("No bundled models found. Please load one externally.\n")
        }
    }

    fun loadExternalModel(uri: Uri) = viewModelScope.launch {
        try {
            printMessage("Importing external model...\n")
            val modelFile = copyUriToFile(uri, modelsPath)
            
            withContext(Dispatchers.IO) {
                printMessage("Loading model into memory: ${modelFile.name}...\n")
                whisperContext?.release()
                whisperContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
            }
            
            canTranscribe = true
            printMessage("External model loaded successfully!\n")
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("Error loading external model: ${e.localizedMessage}\n")
            canTranscribe = false
        }
    }

    fun transcribeAudioFile(uri: Uri) = viewModelScope.launch {
        try {
            printMessage("Importing audio file...\n")
            val audioFile = copyUriToFile(uri, application.cacheDir)
            transcribeAudio(audioFile)
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("Error reading audio: ${e.localizedMessage}\n")
        }
    }

    private suspend fun copyUriToFile(uri: Uri, targetDir: File): File = withContext(Dispatchers.IO) {
        targetDir.mkdirs()
        val fileName = getFileNameFromUri(uri) ?: "imported_file_${System.currentTimeMillis()}"
        val destFile = File(targetDir, fileName)
        
        application.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Failed to open input stream for URI")
        
        return@withContext destFile
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        if (uri.scheme == "content") {
            application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }

    fun benchmark() = viewModelScope.launch {
        runBenchmark(6)
    }

    private suspend fun runBenchmark(nthreads: Int) {
        if (!canTranscribe) return
        canTranscribe = false

        printMessage("Running benchmark. This will take minutes...\n")
        
        withContext(Dispatchers.Default) {
            whisperContext?.benchMemory(nthreads)?.let{ printMessage(it) }
            printMessage("\n")
            whisperContext?.benchGgmlMulMat(nthreads)?.let{ printMessage(it) }
        }

        canTranscribe = true
    }

    private suspend fun readAudioSamples(file: File): FloatArray = withContext(Dispatchers.IO) {
        stopPlayback()
        startPlayback(file)
        return@withContext decodeWaveFile(file)
    }

    private suspend fun stopPlayback() = withContext(Dispatchers.Main) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private suspend fun startPlayback(file: File) = withContext(Dispatchers.Main) {
        mediaPlayer = MediaPlayer.create(application, file.absolutePath.toUri())
        mediaPlayer?.start()
    }

    private suspend fun transcribeAudio(file: File) {
        if (!canTranscribe) return
        canTranscribe = false

        try {
            printMessage("Reading wave samples... ")
            val data = readAudioSamples(file)
            printMessage("${data.size / (16000 / 1000)} ms\n")
            
            printMessage("Transcribing data...\n")
            val start = System.currentTimeMillis()
            
            // Moved to Default dispatcher to prevent UI freeze
            val text = withContext(Dispatchers.Default) {
                whisperContext?.transcribeData(data)
            }
            
            val elapsed = System.currentTimeMillis() - start
            printMessage("Done ($elapsed ms): \n$text\n")
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }

        canTranscribe = true
    }

    fun toggleRecord() = viewModelScope.launch {
        try {
            if (isRecording) {
                recorder.stopRecording()
                isRecording = false
                recordedFile?.let { transcribeAudio(it) }
            } else {
                stopPlayback()
                val file = getTempFileForRecording()
                recorder.startRecording(file) { e ->
                    viewModelScope.launch {
                        withContext(Dispatchers.Main) {
                            printMessage("${e.localizedMessage}\n")
                            isRecording = false
                        }
                    }
                }
                isRecording = true
                recordedFile = file
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            isRecording = false
        }
    }

    private suspend fun getTempFileForRecording() = withContext(Dispatchers.IO) {
        File.createTempFile("recording", "wav", application.cacheDir)
    }

    override fun onCleared() {
        runBlocking {
            whisperContext?.release()
            whisperContext = null
            stopPlayback()
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainScreenViewModel(application)
            }
        }
    }
}

private suspend fun Context.copyData(
    assetDirName: String,
    destDir: File,
    printMessage: suspend (String) -> Unit
) = withContext(Dispatchers.IO) {
    assets.list(assetDirName)?.forEach { name ->
        val assetPath = "$assetDirName/$name"
        Log.v(LOG_TAG, "Processing $assetPath...")
        val destination = File(destDir, name)
        Log.v(LOG_TAG, "Copying $assetPath to $destination...")
        printMessage("Copying $name...\n")
        assets.open(assetPath).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.v(LOG_TAG, "Copied $assetPath to $destination")
    }
}
