package com.whispercppdemo.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.whispercppdemo.R

@Composable
fun MainScreen(viewModel: MainScreenViewModel) {
    MainScreen(
        canTranscribe = viewModel.canTranscribe,
        isRecording = viewModel.isRecording,
        messageLog = viewModel.dataLog,
        onBenchmarkTapped = viewModel::benchmark,
        onTranscribeSampleTapped = viewModel::transcribeSample,
        onRecordTapped = viewModel::toggleRecord,
        onLoadModelTapped = viewModel::loadExternalModel,
        onTranscribeFileTapped = viewModel::transcribeAudioFile
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    canTranscribe: Boolean,
    isRecording: Boolean,
    messageLog: String,
    onBenchmarkTapped: () -> Unit,
    onTranscribeSampleTapped: () -> Unit,
    onRecordTapped: () -> Unit,
    onLoadModelTapped: (android.net.Uri) -> Unit,
    onTranscribeFileTapped: (android.net.Uri) -> Unit
) {
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let { onLoadModelTapped(it) }
        }
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let { onTranscribeFileTapped(it) }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BenchmarkButton(enabled = canTranscribe, onClick = onBenchmarkTapped, modifier = Modifier.weight(1f))
                    TranscribeSampleButton(enabled = canTranscribe, onClick = onTranscribeSampleTapped, modifier = Modifier.weight(1f))
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    LoadModelButton(onClick = { modelPickerLauncher.launch("*/*") }, modifier = Modifier.weight(1f))
                    TranscribeFileButton(enabled = canTranscribe, onClick = { audioPickerLauncher.launch("audio/*") }, modifier = Modifier.weight(1f))
                }
                
                RecordButton(
                    enabled = canTranscribe,
                    isRecording = isRecording,
                    onClick = onRecordTapped,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            MessageLog(messageLog)
        }
    }
}

@Composable
private fun MessageLog(log: String) {
    SelectionContainer {
        Text(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            text = log
        )
    }
}

@Composable
private fun BenchmarkButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text("Benchmark")
    }
}

@Composable
private fun TranscribeSampleButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text("Transcribe Sample")
    }
}

@Composable
private fun LoadModelButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text("Load Model")
    }
}

@Composable
private fun TranscribeFileButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text("Pick Audio File")
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RecordButton(enabled: Boolean, isRecording: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val micPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO,
        onPermissionResult = { granted ->
            if (granted) {
                onClick()
            }
        }
    )
    Button(onClick = {
        if (micPermissionState.status.isGranted) {
            onClick()
        } else {
            micPermissionState.launchPermissionRequest()
        }
    }, enabled = enabled, modifier = modifier) {
        Text(
            if (isRecording) {
                "Stop Recording"
            } else {
                "Start Recording"
            }
        )
    }
}    onRecordTapped: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    BenchmarkButton(enabled = canTranscribe, onClick = onBenchmarkTapped)
                    TranscribeSampleButton(enabled = canTranscribe, onClick = onTranscribeSampleTapped)
                }
                RecordButton(
                    enabled = canTranscribe,
                    isRecording = isRecording,
                    onClick = onRecordTapped
                )
            }
            MessageLog(messageLog)
        }
    }
}

@Composable
private fun MessageLog(log: String) {
    SelectionContainer {
        Text(modifier = Modifier.verticalScroll(rememberScrollState()), text = log)
    }
}

@Composable
private fun BenchmarkButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled) {
        Text("Benchmark")
    }
}

@Composable
private fun TranscribeSampleButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled) {
        Text("Transcribe sample")
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RecordButton(enabled: Boolean, isRecording: Boolean, onClick: () -> Unit) {
    val micPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO,
        onPermissionResult = { granted ->
            if (granted) {
                onClick()
            }
        }
    )
    Button(onClick = {
        if (micPermissionState.status.isGranted) {
            onClick()
        } else {
            micPermissionState.launchPermissionRequest()
        }
     }, enabled = enabled) {
        Text(
            if (isRecording) {
                "Stop recording"
            } else {
                "Start recording"
            }
        )
    }
}
