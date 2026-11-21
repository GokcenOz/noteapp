package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RecordingState(
  val isRecording: Boolean = false,
  val elapsedMs: Long = 0L,
  val amplitudes: List<Float> = emptyList(),
  val recordedBytes: ByteArray? = null,
  val recordedDurationMs: Long = 0L,
  val errorMessage: String? = null,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RecordingState) return false
    return isRecording == other.isRecording &&
      elapsedMs == other.elapsedMs &&
      amplitudes == other.amplitudes &&
      recordedBytes.contentEquals(other.recordedBytes) &&
      recordedDurationMs == other.recordedDurationMs &&
      errorMessage == other.errorMessage
  }

  override fun hashCode(): Int {
    var result = isRecording.hashCode()
    result = 31 * result + elapsedMs.hashCode()
    result = 31 * result + amplitudes.hashCode()
    result = 31 * result + (recordedBytes?.contentHashCode() ?: 0)
    result = 31 * result + recordedDurationMs.hashCode()
    result = 31 * result + (errorMessage?.hashCode() ?: 0)
    return result
  }
}

data class PlaybackState(
  val activeId: String? = null,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val errorMessage: String? = null,
)

class VoiceAudioManager(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var recorder: MediaRecorder? = null
  private var recordingFile: File? = null
  private var recordingJob: Job? = null
  private var recordingStartTimeMs: Long = 0L

  private var player: MediaPlayer? = null
  private var playbackFile: File? = null
  private var playbackJob: Job? = null

  private val _recordingState = MutableStateFlow(RecordingState())
  val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

  private val _playbackState = MutableStateFlow(PlaybackState())
  val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

  fun startRecording() {
    stopPlayback()
    releaseRecorder()
    val outFile = File(appContext.cacheDir, "nb_rec_${System.currentTimeMillis()}.m4a")
    recordingFile = outFile

    try {
      val mr =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          MediaRecorder(appContext)
        } else {
          @Suppress("DEPRECATION") MediaRecorder()
        }
      mr.setAudioSource(MediaRecorder.AudioSource.MIC)
      mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      mr.setAudioChannels(1)
      mr.setAudioSamplingRate(16_000)
      mr.setAudioEncodingBitRate(32_000)
      mr.setMaxDuration(MAX_DURATION_MS.toInt())
      mr.setOutputFile(outFile.absolutePath)
      mr.setOnInfoListener { _, what, _ ->
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
          stopRecording()
        }
      }
      mr.prepare()
      mr.start()
      recorder = mr
      recordingStartTimeMs = System.currentTimeMillis()

      _recordingState.value =
        RecordingState(isRecording = true, elapsedMs = 0L, amplitudes = emptyList())

      recordingJob?.cancel()
      recordingJob =
        scope.launch {
          while (isActive) {
            val elapsed = System.currentTimeMillis() - recordingStartTimeMs
            val amp =
              try {
                ((recorder?.maxAmplitude ?: 0) / 32767f).coerceIn(0.06f, 1f)
              } catch (_: Exception) {
                0.08f
              }
            _recordingState.update { current ->
              current.copy(
                elapsedMs = elapsed,
                amplitudes = (current.amplitudes + amp).takeLast(48),
              )
            }
            if (elapsed >= MAX_DURATION_MS) {
              stopRecording()
              break
            }
            delay(85L)
          }
        }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start MediaRecorder", e)
      releaseRecorder()
      outFile.delete()
      _recordingState.value =
        RecordingState(errorMessage = "Ses kaydı başlatılamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}")
    }
  }

  fun stopRecording() {
    recordingJob?.cancel()
    recordingJob = null
    val mr = recorder ?: return
    val duration = (System.currentTimeMillis() - recordingStartTimeMs).coerceAtLeast(300L)
    try {
      mr.stop()
    } catch (e: Exception) {
      Log.w(TAG, "MediaRecorder stop warning", e)
    } finally {
      mr.reset()
      mr.release()
      recorder = null
    }

    val file = recordingFile
    val bytes =
      if (file != null && file.exists() && file.length() > 0L) {
        try {
          file.readBytes()
        } catch (e: Exception) {
          Log.e(TAG, "Failed reading recorded bytes", e)
          null
        } finally {
          file.delete()
        }
      } else {
        null
      }
    recordingFile = null

    _recordingState.update { current ->
      current.copy(
        isRecording = false,
        recordedBytes = bytes,
        recordedDurationMs = if (bytes != null) duration else 0L,
        errorMessage = if (bytes == null) "Ses kaydı boş görünüyor." else null,
      )
    }
  }

  fun setExistingAudio(bytes: ByteArray?, durationMs: Long) {
    _recordingState.value =
      RecordingState(
        isRecording = false,
        elapsedMs = durationMs,
        recordedBytes = bytes,
        recordedDurationMs = durationMs,
      )
  }

  fun clearRecordedAudio() {
    if (_recordingState.value.isRecording) {
      stopRecording()
    }
    stopPlayback()
    _recordingState.value = RecordingState()
  }

  fun playBytes(playbackId: String, audioBytes: ByteArray, fallbackDurationMs: Long = 0L) {
    val current = _playbackState.value
    if (current.activeId == playbackId && player != null) {
      togglePlayPause()
      return
    }
    stopPlayback()

    try {
      val temp = File(appContext.cacheDir, "nb_play_${System.currentTimeMillis()}.m4a")
      temp.writeBytes(audioBytes)
      playbackFile = temp

      val mp =
        MediaPlayer().apply {
          setDataSource(temp.absolutePath)
          prepare()
          setOnCompletionListener {
            playbackJob?.cancel()
            _playbackState.update {
              it.copy(isPlaying = false, positionMs = 0L)
            }
            try {
              seekTo(0)
            } catch (_: Exception) {}
          }
          start()
        }
      player = mp
      val totalDuration = mp.duration.toLong().takeIf { it > 0L } ?: fallbackDurationMs
      _playbackState.value =
        PlaybackState(
          activeId = playbackId,
          isPlaying = true,
          positionMs = 0L,
          durationMs = totalDuration,
        )
      startPlaybackTicker()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to play decrypted audio", e)
      stopPlayback()
      _playbackState.value =
        PlaybackState(errorMessage = "Ses oynatılamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}")
    }
  }

  fun togglePlayPause() {
    val mp = player ?: return
    try {
      if (mp.isPlaying) {
        mp.pause()
        playbackJob?.cancel()
        _playbackState.update { it.copy(isPlaying = false, positionMs = mp.currentPosition.toLong()) }
      } else {
        mp.start()
        _playbackState.update { it.copy(isPlaying = true) }
        startPlaybackTicker()
      }
    } catch (e: Exception) {
      Log.w(TAG, "togglePlayPause failed", e)
      stopPlayback()
    }
  }

  fun seekTo(positionMs: Long) {
    val mp = player ?: return
    try {
      mp.seekTo(positionMs.toInt())
      _playbackState.update { it.copy(positionMs = positionMs) }
    } catch (e: Exception) {
      Log.w(TAG, "seekTo failed", e)
    }
  }

  fun stopPlayback() {
    playbackJob?.cancel()
    playbackJob = null
    try {
      player?.stop()
    } catch (_: Exception) {}
    try {
      player?.release()
    } catch (_: Exception) {}
    player = null
    playbackFile?.delete()
    playbackFile = null
    _playbackState.value = PlaybackState()
  }

  private fun startPlaybackTicker() {
    playbackJob?.cancel()
    playbackJob =
      scope.launch {
        while (isActive) {
          val mp = player ?: break
          val playing =
            try {
              mp.isPlaying
            } catch (_: Exception) {
              false
            }
          if (!playing) break
          val pos =
            try {
              mp.currentPosition.toLong()
            } catch (_: Exception) {
              0L
            }
          _playbackState.update { it.copy(positionMs = pos, isPlaying = true) }
          delay(100L)
        }
      }
  }

  private fun releaseRecorder() {
    recordingJob?.cancel()
    recordingJob = null
    try {
      recorder?.release()
    } catch (_: Exception) {}
    recorder = null
  }

  fun release() {
    releaseRecorder()
    recordingFile?.delete()
    recordingFile = null
    stopPlayback()
  }

  companion object {
    private const val TAG = "VoiceAudioManager"
    const val MAX_DURATION_MS = 120_000L
  }
}
