// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.proof

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

/**
 * A proof photo full screen, as the Figma frame `S5 · Proof photo · full screen`: pinch to zoom,
 * drag to move, double-tap to fit again. Close or Back calls [onClose].
 *
 * Shared by the explorer's completion form and the venue's Review proof screen, which both already
 * hold the photo as an [ImageBitmap].
 *
 * @param title Said at the top: who sent the photo, or the venue it's for.
 * @param subtitle Under [title], or nothing when null.
 */
@Composable
fun ProofPhotoViewer(
    image: ImageBitmap,
    contentDescription: String,
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
) {
  Dialog(
      onDismissRequest = onClose,
      properties =
          DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    ProofPhotoViewerContent(image, contentDescription, title, subtitle, onClose)
  }
}

/** The full screen of [ProofPhotoViewer], without its window, so a preview can draw it. */
@Composable
internal fun ProofPhotoViewerContent(
    image: ImageBitmap,
    contentDescription: String,
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // Dark in both themes, as Figma draws it: the photo reads best on a dark background
  AroundAppTheme(darkTheme = true) {
    var zoom by remember(image) { mutableStateOf(PhotoZoom()) }
    var area by remember { mutableStateOf(Size.Zero) }
    val photo = Size(image.width.toFloat(), image.height.toFloat())
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(C.Tag.PROOF_VIEWER)
    ) {
      TopBar(title, subtitle, onClose)
      // Between the top bar and the zoom pill, as Figma centres it, so no part of the zoomed photo
      // can hide behind them: each edge can be dragged into view
      Image(
          image,
          contentDescription,
          // The gestures read the area's pixels, before the zoom moves the photo
          Modifier.fillMaxWidth()
              .weight(1f)
              .clipToBounds()
              .onSizeChanged { area = it.toSize() }
              .pointerInput(photo) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                  zoom = zoom.transformed(centroid, pan, gestureZoom, photo, area)
                }
              }
              .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = PhotoZoom() }) }
              .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
              }
              .testTag(C.Tag.PROOF_VIEWER_IMAGE),
          contentScale = ContentScale.Fit,
      )
      ZoomLevel(
          zoom.scale,
          Modifier.align(Alignment.CenterHorizontally)
              .navigationBarsPadding()
              .padding(start = AroundSpacing.SectionGap, end = AroundSpacing.SectionGap)
              .padding(bottom = ZOOM_LEVEL_BOTTOM),
      )
    }
  }
}

/** Close, then the [title] above the [subtitle]. */
@Composable
private fun TopBar(title: String, subtitle: String?, onClose: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
      Modifier.fillMaxWidth()
          .statusBarsPadding()
          .padding(
              start = AroundSpacing.SectionGap,
              top = AroundSpacing.SectionGap,
              end = AroundSpacing.SectionGap,
              bottom = AroundSpacing.Large,
          ),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    FilledIconButton(
        onClick = onClose,
        modifier = Modifier.size(CLOSE_SIZE).testTag(C.Tag.PROOF_VIEWER_CLOSE),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.surface,
                contentColor = colors.onSurface,
            ),
    ) {
      Icon(
          painterResource(R.drawable.ic_viewer_close),
          stringResource(R.string.proof_viewer_close),
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TITLES_GAP)) {
      Text(
          title,
          Modifier.testTag(C.Tag.PROOF_VIEWER_TITLE),
          color = colors.onBackground,
          style = AroundTextStyles.RowTitle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      if (subtitle != null) {
        Text(
            subtitle,
            Modifier.testTag(C.Tag.PROOF_VIEWER_SUBTITLE),
            color = colors.onBackground.copy(alpha = SUBTITLE_ALPHA),
            style = AroundTextStyles.Caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/** How far the photo is zoomed in, e.g. "2×", then how to zoom, move and fit it. */
@Composable
private fun ZoomLevel(scale: Float, modifier: Modifier) {
  val locale = LocalConfiguration.current.locales[0]
  Surface(
      modifier,
      shape = AroundPillShape,
      color = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Row(
        Modifier.padding(
            start = AroundSpacing.InsetPadding,
            end = AroundSpacing.Large,
            top = AroundSpacing.CompactGap,
            bottom = AroundSpacing.CompactGap,
        ),
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          stringResource(R.string.proof_viewer_zoom_level, zoomLevel(scale, locale)),
          Modifier.testTag(C.Tag.PROOF_VIEWER_ZOOM),
          style = ZOOM_STYLE,
      )
      Text(
          stringResource(R.string.proof_viewer_hint),
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = HINT_ALPHA),
          style = AroundTextStyles.Caption,
      )
    }
  }
}

private val CLOSE_SIZE = 44.dp
private val TITLES_GAP = 2.dp
private const val SUBTITLE_ALPHA = 0.72f
private const val HINT_ALPHA = 0.8f
private val ZOOM_STYLE = AroundTextStyles.AvatarSmall.copy(fontSize = 16.sp)
// Figma's 48dp under the pill, less the navigation bar it draws in that space
private val ZOOM_LEVEL_BOTTOM = 34.dp

// Internal, not private, so a test can draw it: the preview is code too. The viewer is dark in
// both themes, so one preview shows it in light and dark mode
@Preview(name = "Full screen", widthDp = 413, heightDp = 917)
@Composable
internal fun ProofPhotoViewerPreview() {
  ProofPhotoViewerContent(
      ImageBitmap(PREVIEW_PHOTO_WIDTH, PREVIEW_PHOTO_HEIGHT),
      contentDescription = "",
      title = "yusuf.s",
      subtitle = "Find the hidden fox · sent 2 min. ago",
      onClose = {},
  )
}

private const val PREVIEW_PHOTO_WIDTH = 783
private const val PREVIEW_PHOTO_HEIGHT = 630
