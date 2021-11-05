package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerToolbar
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.ExoPlayerWrapper
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.ui.widget.SimpleAnimatorListener
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class AudioPlayerView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attributeSet, defAttrStyle), WindowInsetsListener {
  private lateinit var audioPlayerMuteUnmute: ImageButton
  private lateinit var audioPlayerPlayPause: ImageButton
  private lateinit var audioPlayerRestart: ImageButton
  private lateinit var audioPlayerControlsRoot: LinearLayout
  private lateinit var audioPlayerPositionDuration: TextView

  private var hasSoundPostUrl: Boolean = false
  private var positionAndDurationUpdateJob: Job? = null
  private var hideShowAnimation: ValueAnimator? = null

  private lateinit var audioPlayerViewState: AudioPlayerViewState
  private lateinit var mediaViewContract: MediaViewContract
  private lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  private lateinit var threadDownloadManager: ThreadDownloadManager
  private lateinit var cachedHttpDataSourceFactory: DataSource.Factory
  private lateinit var fileDataSourceFactory: DataSource.Factory
  private lateinit var contentDataSourceFactory: DataSource.Factory

  private val scope = KurobaCoroutineScope()
  private val cancellableToast by lazy { CancellableToast() }

  private val soundPostVideoPlayerLazy = lazy {
    ExoPlayerWrapper(
      context = context,
      threadDownloadManager = threadDownloadManager,
      cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
      fileDataSourceFactory = fileDataSourceFactory,
      contentDataSourceFactory = contentDataSourceFactory,
      mediaViewContract = mediaViewContract,
      onAudioDetected = {}
    )
  }

  private val soundPostVideoPlayer: ExoPlayerWrapper
    get() = soundPostVideoPlayerLazy.value

  init {
    inflate(context, R.layout.audio_player_control_view, this)
  }

  fun bind(
    viewableMedia: ViewableMedia,
    audioPlayerViewState: AudioPlayerViewState,
    mediaViewContract: MediaViewContract,
    globalWindowInsetsManager: GlobalWindowInsetsManager,
    threadDownloadManager: ThreadDownloadManager,
    cachedHttpDataSourceFactory: DataSource.Factory,
    fileDataSourceFactory: DataSource.Factory,
    contentDataSourceFactory: DataSource.Factory,
  ) {
    this.hasSoundPostUrl = viewableMedia.viewableMediaMeta.soundPostActualSoundMedia != null
    if (!hasSoundPostUrl) {
      return
    }

    this.audioPlayerViewState = audioPlayerViewState
    this.mediaViewContract = mediaViewContract
    this.globalWindowInsetsManager = globalWindowInsetsManager
    this.threadDownloadManager = threadDownloadManager
    this.cachedHttpDataSourceFactory = cachedHttpDataSourceFactory
    this.fileDataSourceFactory = fileDataSourceFactory
    this.contentDataSourceFactory = contentDataSourceFactory

    audioPlayerControlsRoot = findViewById(R.id.audio_player_controls_view_root)
    audioPlayerMuteUnmute = findViewById(R.id.audio_player_mute_unmute)
    audioPlayerPlayPause = findViewById(R.id.audio_player_play_pause)
    audioPlayerRestart = findViewById(R.id.audio_player_restart)
    audioPlayerPositionDuration = findViewById(R.id.audio_player_position_duration)
    audioPlayerControlsRoot.visibility = View.GONE

    audioPlayerMuteUnmute.setOnClickListener {
      mediaViewContract.toggleSoundMuteState()
      val isSoundCurrentlyMuted = mediaViewContract.isSoundCurrentlyMuted()

      if (isSoundCurrentlyMuted) {
        soundPostVideoPlayer.muteUnMute(true)
      } else {
        soundPostVideoPlayer.muteUnMute(false)
      }

      updateAudioIcon(isSoundCurrentlyMuted)
    }

    audioPlayerPlayPause.setOnClickListener {
      val nowPlaying = soundPostVideoPlayer.isPlaying().not()

      if (nowPlaying) {
        soundPostVideoPlayer.start()
      } else {
        soundPostVideoPlayer.pause()
      }

      updatePlayIcon(nowPlaying)
    }

    positionAndDurationUpdateJob?.cancel()
    positionAndDurationUpdateJob = scope.launch(start = CoroutineStart.LAZY) {
      soundPostVideoPlayer.positionAndDurationFlow.collect { (position, duration) ->
        updatePlayerPositionDuration(position, duration)
      }
    }

    audioPlayerRestart.setOnClickListener {
      soundPostVideoPlayer.resetPosition()
    }

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  fun show(isLifecycleChange: Boolean) {
    if (!hasSoundPostUrl) {
      return
    }
  }

  fun hide(isLifecycleChange: Boolean) {
    if (!hasSoundPostUrl) {
      return
    }

    if (soundPostVideoPlayerLazy.isInitialized() && soundPostVideoPlayer.hasContent) {
      audioPlayerViewState.prevPosition = soundPostVideoPlayer.actualExoPlayer.currentPosition
      audioPlayerViewState.prevWindowIndex = soundPostVideoPlayer.actualExoPlayer.currentWindowIndex

      if (audioPlayerViewState.prevPosition <= 0 && audioPlayerViewState.prevWindowIndex <= 0) {
        // Reset the flag because (most likely) the user swiped through the pages so fast that the
        // player hasn't been able to start playing so it's still in some kind of BUFFERING state or
        // something like that so mainVideoPlayer.isPlaying() will return false which will cause the
        // player to appear paused if the user switches back to this page. We don't want that that's
        // why we are resetting the "playing" to null here.
        audioPlayerViewState.playing = null
      } else {
        audioPlayerViewState.playing = soundPostVideoPlayer.isPlaying()
      }

      soundPostVideoPlayer.pause()
    }
  }

  fun unbind() {
    hasSoundPostUrl = false

    if (soundPostVideoPlayerLazy.isInitialized() && soundPostVideoPlayer.hasContent) {
      soundPostVideoPlayer.release()
    }

    positionAndDurationUpdateJob?.cancel()
    positionAndDurationUpdateJob = null

    if (::globalWindowInsetsManager.isInitialized) {
      globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    }

    cancellableToast.cancel()
    scope.cancelChildren()
  }

  fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {
    if (!hasSoundPostUrl) {
      return
    }

    if (soundPostVideoPlayer.hasContent) {
      if (systemUIHidden) {
        hideAudioPlayerView()
      } else {
        showAudioPlayerView()
      }
    }
  }

  override fun onInsetsChanged() {
    audioPlayerControlsRoot.updatePaddings(
      bottom = globalWindowInsetsManager.bottom(),
      left = globalWindowInsetsManager.left(),
      right = globalWindowInsetsManager.right()
    )
  }

  suspend fun stop() {
    if (!hasSoundPostUrl) {
      return
    }

    if (soundPostVideoPlayerLazy.isInitialized() && soundPostVideoPlayer.isPlaying()) {
      soundPostVideoPlayer.pause()
    }
  }

  suspend fun loadAndPlaySoundPostAudioIfPossible(isLifecycleChange: Boolean, viewableMedia: ViewableMedia) {
    if (!hasSoundPostUrl) {
      return
    }

    if (soundPostVideoPlayerLazy.isInitialized() && soundPostVideoPlayer.isPlaying()) {
      showAudioPlayerView()
      onInsetsChanged()
      onAudioPlaying()

      return
    }

    val soundPostActualSoundMedia = viewableMedia.viewableMediaMeta.soundPostActualSoundMedia
    if (soundPostActualSoundMedia != null) {
      if (loadImageBgAudio(isLifecycleChange, soundPostActualSoundMedia)) {
        positionAndDurationUpdateJob?.start()
        showAudioPlayerView()
        onInsetsChanged()
        onAudioPlaying()

        return
      }

      // fallthrough
    }

    audioPlayerControlsRoot.visibility = GONE
  }

  private fun onAudioPlaying() {
    updateAudioIcon(mediaViewContract.isSoundCurrentlyMuted())
    updatePlayIcon(soundPostVideoPlayer.isPlaying())
  }

  private suspend fun loadImageBgAudio(
    isLifecycleChange: Boolean,
    soundPostActualSoundMedia: ViewableMedia.Audio
  ): Boolean {
    try {
      if (!isLifecycleChange && ChanSettings.videoAlwaysResetToStart.get()) {
        audioPlayerViewState.resetPosition()
        soundPostVideoPlayer.resetPosition()
      }

      soundPostVideoPlayer.preload(
        viewableMedia = soundPostActualSoundMedia,
        mediaLocation = soundPostActualSoundMedia.mediaLocation,
        prevPosition = audioPlayerViewState.prevPosition,
        prevWindowIndex = audioPlayerViewState.prevWindowIndex
      )

      if (audioPlayerViewState.playing == null || audioPlayerViewState.playing == true) {
        soundPostVideoPlayer.startAndAwaitFirstFrame()
      } else if (audioPlayerViewState.prevWindowIndex >= 0 && audioPlayerViewState.prevPosition >= 0) {
        // We need to do this hacky stuff to force exoplayer to show the video frame instead of nothing
        // after the activity is paused and then unpaused (like when the user turns off/on the phone
        // screen).
        val newPosition = (audioPlayerViewState.prevPosition - ExoPlayerWrapper.SEEK_POSITION_DELTA).coerceAtLeast(0)
        soundPostVideoPlayer.seekTo(audioPlayerViewState.prevWindowIndex, newPosition)
      }

      updatePlayerPositionDuration(
        soundPostVideoPlayer.actualExoPlayer.currentPosition,
        soundPostVideoPlayer.actualExoPlayer.duration
      )

      return true
    } catch (error: Throwable) {
      Logger.e(TAG, "Failed to load image bg audio: ${soundPostActualSoundMedia.mediaLocation}", error)
      cancellableToast.showToast(context, "Failed to load image bg audio, error=${error.errorMessageOrClassName()}")

      return false
    }
  }

  private fun updateAudioIcon(soundCurrentlyMuted: Boolean) {
    val imageDrawable =  if (soundCurrentlyMuted) {
      R.drawable.ic_volume_off_white_24dp
    } else {
      R.drawable.ic_volume_up_white_24dp
    }

    audioPlayerMuteUnmute.setImageResource(imageDrawable)
  }

  private fun updatePlayIcon(nowPlaying: Boolean) {
    val imageDrawable = if (nowPlaying) {
      R.drawable.exo_controls_pause
    } else {
      R.drawable.exo_controls_play
    }

    audioPlayerPlayPause.setImageResource(imageDrawable)
  }

  private fun updatePlayerPositionDuration(position: Long, duration: Long) {
    val positionMsFormatted = TimeUtils.formatPeriod(position)
    val durationMsFormatted = TimeUtils.formatPeriod(duration)

    audioPlayerPositionDuration.setText("${positionMsFormatted} / $durationMsFormatted")
  }

  private fun hideAudioPlayerView() {
    if (hideShowAnimation != null) {
      hideShowAnimation?.end()
      hideShowAnimation = null
    }

    if (audioPlayerControlsRoot.visibility == View.GONE) {
      return
    }

    hideShowAnimation = ValueAnimator.ofFloat(1f, 0f).apply {
      duration = MediaViewerToolbar.ANIMATION_DURATION_MS
      addUpdateListener { animation ->
        audioPlayerControlsRoot.alpha = animation.animatedValue as Float
      }
      addListener(object : SimpleAnimatorListener() {
        override fun onAnimationStart(animation: Animator?) {
          audioPlayerControlsRoot.alpha = 1f
        }

        override fun onAnimationEnd(animation: Animator?) {
          audioPlayerControlsRoot.alpha = 0f
          audioPlayerControlsRoot.setVisibilityFast(View.GONE)
          hideShowAnimation = null
        }
      })
      start()
    }
  }

  private fun showAudioPlayerView() {
    if (hideShowAnimation != null) {
      hideShowAnimation?.end()
      hideShowAnimation = null
    }

    if (audioPlayerControlsRoot.visibility == View.VISIBLE) {
      return
    }

    hideShowAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = MediaViewerToolbar.ANIMATION_DURATION_MS
      addUpdateListener { animation ->
        audioPlayerControlsRoot.alpha = animation.animatedValue as Float
      }
      addListener(object : SimpleAnimatorListener() {
        override fun onAnimationStart(animation: Animator?) {
          audioPlayerControlsRoot.alpha = 0f
        }

        override fun onAnimationEnd(animation: Animator?) {
          audioPlayerControlsRoot.alpha = 1f
          audioPlayerControlsRoot.setVisibilityFast(View.VISIBLE)
          hideShowAnimation = null
        }
      })
      start()
    }
  }

  @Parcelize
  class AudioPlayerViewState(
    var prevPosition: Long = 0,
    var prevWindowIndex: Int = 0,
    var playing: Boolean? = null
  ) : MediaViewState(null), Parcelable {

    override fun resetPosition() {
      super.resetPosition()

      prevPosition = 0
      prevWindowIndex = 0
    }

    override fun clone(): MediaViewState {
      return AudioPlayerViewState(prevPosition, prevWindowIndex, playing)
    }

    override fun updateFrom(other: MediaViewState?) {
      if (other !is AudioPlayerViewState) {
        return
      }

      this.prevPosition = other.prevPosition
      this.prevWindowIndex = other.prevWindowIndex
      this.playing = other.playing
    }
  }

  companion object {
    private const val TAG = "AudioPlayerView"
  }

}