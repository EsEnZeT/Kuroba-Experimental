/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.features.filters

import android.content.Context
import android.net.Uri
import android.text.Html
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.Divider
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.usecase.ExportFiltersUseCase
import com.github.k1rakishou.chan.core.usecase.ImportFiltersUseCase
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.KurobaComposeClickableText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.KurobaComposeSwitch
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableState
import com.github.k1rakishou.chan.ui.compose.reorder.detectReorder
import com.github.k1rakishou.chan.ui.compose.reorder.draggedItem
import com.github.k1rakishou.chan.ui.compose.reorder.rememberReorderState
import com.github.k1rakishou.chan.ui.compose.reorder.reorderable
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController.ToolbarSearchCallback
import com.github.k1rakishou.chan.ui.theme.SimpleSquarePainter
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import javax.inject.Inject

class FiltersController(
  context: Context,
  private val chanFilterMutable: ChanFilterMutable?
) :
  Controller(context),
  ToolbarSearchCallback,
  WindowInsetsListener {

  @Inject
  lateinit var filterEngine: FilterEngine
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var exportFiltersUseCase: ExportFiltersUseCase
  @Inject
  lateinit var importFiltersUseCase: ImportFiltersUseCase

  private var bottomPadding = mutableStateOf(0)

  private val viewModel by lazy {
    requireComponentActivity().viewModelByKey<FiltersControllerViewModel>()
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.setTitle(R.string.filters_screen)
    navigation.swipeable = false

    navigation
      .buildMenu(context)
      .withItem(R.drawable.ic_search_white_24dp) { item -> searchClicked(item) }
      .withOverflow(requireNavController())
      .withSubItem(ACTION_EXPORT_FILTERS, R.string.filters_controller_export_action, { item -> exportFilters(item) })
      .withSubItem(ACTION_IMPORT_FILTERS, R.string.filters_controller_import_action, { item -> importFilters(item) })
      .withSubItem(ACTION_SHOW_HELP, R.string.filters_controller_show_help, { item -> helpClicked(item) })
      .build()
      .build()

    onInsetsChanged()
    globalWindowInsetsManager.addInsetsUpdatesListener(this)

    mainScope.launch { viewModel.reloadFilters() }

    if (chanFilterMutable != null) {
      mainScope.launch {
        viewModel.awaitUntilDependenciesInitialized()
        delay(32L)

        showCreateNewFilterController(chanFilterMutable)
      }
    }

    view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          val chanTheme = LocalChanTheme.current

          Box(modifier = Modifier
            .fillMaxSize()
            .background(chanTheme.backColorCompose)
          ) {
            BuildContent()
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    val bottomPaddingDp = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = null
    )

    bottomPadding.value = bottomPaddingDp
  }

  override fun onSearchVisibilityChanged(visible: Boolean) {
    // TODO(KurobaEx-filters): search
  }

  override fun onSearchEntered(entered: String) {
    // TODO(KurobaEx-filters): search
  }

  @Composable
  private fun BuildContent() {
    val chanTheme = LocalChanTheme.current
    val filters = remember { viewModel.filters }
    val coroutineScope = rememberCoroutineScope()
    val bottomPd by bottomPadding
    val contentPadding = PaddingValues(bottom = bottomPd.dp + FAB_SIZE + (FAB_MARGIN / 2))
    val reoderableState = rememberReorderState()

    Box(modifier = Modifier
      .fillMaxSize()
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .reorderable(
            state = reoderableState,
            onMove = { from, to -> viewModel.reorderFilterInMemory(from, to) },
            onDragEnd = { _, _ -> viewModel.persistReorderedFilters() }
          )
          .simpleVerticalScrollbar(
            state = reoderableState.listState,
            chanTheme = chanTheme,
            contentPadding = contentPadding
          ),
        state = reoderableState.listState,
        contentPadding = contentPadding
      ) {
        items(filters.size) { index ->
          val chanFilterInfo = filters.get(index)

          BuildChanFilter(
            index = index,
            totalCount = filters.size,
            reoderableState = reoderableState,
            chanFilterInfo = chanFilterInfo,
            coroutineScope = coroutineScope,
            onFilterClicked = { clickedFilter ->
              showCreateNewFilterController(ChanFilterMutable.from(clickedFilter))
            }
          )
        }
      }

      FloatingActionButton(
        modifier = Modifier
          .size(FAB_SIZE)
          .align(Alignment.BottomEnd)
          .offset(x = -FAB_MARGIN, y = -(bottomPd.dp + (FAB_MARGIN / 2))),
        backgroundColor = chanTheme.accentColorCompose,
        contentColor = Color.White,
        onClick = { showCreateNewFilterController(null) }
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_add_white_24dp),
          contentDescription = null
        )
      }
    }
  }

  @Composable
  private fun BuildChanFilter(
    index: Int,
    totalCount: Int,
    reoderableState: ReorderableState,
    chanFilterInfo: FiltersControllerViewModel.ChanFilterInfo,
    coroutineScope: CoroutineScope,
    onFilterClicked: (ChanFilter) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .draggedItem(reoderableState.offsetByIndex(index))
        .kurobaClickable(bounded = true, onClick = { onFilterClicked(chanFilterInfo.chanFilter) })
        .background(color = chanTheme.backColorCompose)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        val squareDrawableInlineContent = remember(key1 = chanFilterInfo) {
          getSquareDrawableContent(chanFilterInfo = chanFilterInfo)
        }

        val fullText = remember(key1 = chanFilterInfo.filterText) {
          return@remember buildAnnotatedString {
            append("#")
            append((index + 1).toString())
            append(" ")
            append("\n")

            append(chanFilterInfo.filterText)
          }
        }

        KurobaComposeClickableText(
          modifier = Modifier
            .weight(1f)
            .wrapContentHeight()
            .padding(horizontal = 8.dp, vertical = 4.dp),
          text = fullText,
          inlineContent = squareDrawableInlineContent,
          onTextClicked = { textLayoutResult, position -> handleClickedText(textLayoutResult, position) }
        )

        Column(
          modifier = Modifier
            .fillMaxHeight()
            .width(42.dp)
            .padding(end = 8.dp)
        ) {
          KurobaComposeSwitch(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .padding(all = 4.dp),
            initiallyChecked = chanFilterInfo.chanFilter.enabled,
            onCheckedChange = { nowChecked ->
              if (reoderableState.draggedIndex != null) {
                return@KurobaComposeSwitch
              }

              coroutineScope.launch {
                viewModel.enableOrDisableFilter(nowChecked, chanFilterInfo.chanFilter)
              }
            }
          )

          Spacer(modifier = Modifier.weight(1f))

          KurobaComposeIcon(
            modifier = Modifier
              .fillMaxWidth()
              .height(32.dp)
              .padding(all = 4.dp)
              .detectReorder(reoderableState),
            drawableId = R.drawable.ic_baseline_reorder_24,
            themeEngine = themeEngine
          )
        }

      }

      if (index in 0 until (totalCount - 1)) {
        Divider(
          modifier = Modifier.padding(horizontal = 4.dp),
          color = chanTheme.dividerColorCompose,
          thickness = 1.dp
        )
      }
    }
  }

  private fun handleClickedText(
    textLayoutResult: TextLayoutResult,
    position: Int
  ): Boolean {
    val linkAnnotation = textLayoutResult.layoutInput.text
      .getStringAnnotations(FiltersControllerViewModel.LINK_TAG, position, position)
      .firstOrNull()

    if (linkAnnotation != null) {
      val urlToOpen = textLayoutResult.layoutInput.text
        .substring(linkAnnotation.start, linkAnnotation.end)
        .toHttpUrlOrNull()

      if (urlToOpen != null) {
        openLink(urlToOpen.toString())
        return true
      }
    }

    return false
  }

  private fun importFilters(toolbarMenuSubItem: ToolbarMenuSubItem) {
    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleText = getString(R.string.filters_controller_import_warning),
      descriptionText = getString(R.string.filters_controller_import_warning_description),
      positiveButtonText = getString(R.string.filters_controller_do_import),
      negativeButtonText = getString(R.string.filters_controller_do_not_import),
      onPositiveButtonClickListener = {
        fileChooser.openChooseFileDialog(object : FileChooserCallback() {
          override fun onCancel(reason: String) {
            showToast(R.string.canceled)
          }

          override fun onResult(uri: Uri) {
            mainScope.launch {
              val params = ImportFiltersUseCase.Params(uri)

              when (val result = importFiltersUseCase.execute(params)) {
                is ModularResult.Value -> {
                  showToast(R.string.done)
                }
                is ModularResult.Error -> {
                  Logger.e(TAG, "importFilters()", result.error)

                  val message = getString(
                    R.string.filters_controller_import_error,
                    result.error.errorMessageOrClassName()
                  )

                  showToast(message)
                }
              }
            }
          }
        })
      }
    )
  }

  private fun exportFilters(toolbarMenuSubItem: ToolbarMenuSubItem) {
    val dateString = FILTER_DATE_FORMAT.print(DateTime.now())
    val exportFileName = "KurobaEx_exported_filters_($dateString).json"

    fileChooser.openCreateFileDialog(exportFileName, object : FileCreateCallback() {
      override fun onCancel(reason: String) {
        showToast(R.string.canceled)
      }

      override fun onResult(uri: Uri) {
        val params = ExportFiltersUseCase.Params(uri)

        when (val result = exportFiltersUseCase.execute(params)) {
          is ModularResult.Value -> {
            showToast(R.string.done)
          }
          is ModularResult.Error -> {
            Logger.e(TAG, "exportFilters()", result.error)

            val message = getString(
              R.string.filters_controller_export_error,
              result.error.errorMessageOrClassName()
            )

            showToast(message)
          }
        }
      }
    })
  }

  private fun searchClicked(item: ToolbarMenuItem) {
    (navigationController as ToolbarNavigationController).showSearch()
  }

  private fun helpClicked(item: ToolbarMenuSubItem) {
    DialogFactory.Builder.newBuilder(context, dialogFactory)
      .withTitle(R.string.help)
      .withDescription(Html.fromHtml(AppModuleAndroidUtils.getString(R.string.filters_controller_help_message)))
      .withCancelable(true)
      .withNegativeButtonTextId(R.string.filters_controller_open_regex101)
      .withOnNegativeButtonClickListener { openLink("https://regex101.com/") }
      .create()
  }

  private fun showCreateNewFilterController(chanFilterMutable: ChanFilterMutable?) {
    val createOrUpdateFilterController = CreateOrUpdateFilterController(
      context = context,
      previousChanFilterMutable = chanFilterMutable,
      activeBoardsCountForAllSites = viewModel.activeBoardsCountForAllSites()
    )

    presentController(createOrUpdateFilterController)
  }

  private fun getSquareDrawableContent(
    chanFilterInfo: FiltersControllerViewModel.ChanFilterInfo
  ): Map<String, InlineTextContent> {
    val placeholder = Placeholder(
      width = 12.sp,
      height = 12.sp,
      placeholderVerticalAlign = PlaceholderVerticalAlign.Center
    )

    val children: @Composable (String) -> Unit = {
      val dropdownArrowPainter = remember(key1 = chanFilterInfo.chanFilter.color) {
        return@remember SimpleSquarePainter(
          color = Color(chanFilterInfo.chanFilter.color),
        )
      }

      Image(
        modifier = Modifier.fillMaxSize(),
        painter = dropdownArrowPainter,
        contentDescription = null
      )
    }

    return mapOf(
      FiltersControllerViewModel.squareDrawableKey to InlineTextContent(placeholder = placeholder, children = children)
    )
  }

  companion object {
    private const val TAG = "FiltersController"

    private val FAB_SIZE = 52.dp
    private val FAB_MARGIN = 16.dp

    private const val ACTION_EXPORT_FILTERS = 0
    private const val ACTION_IMPORT_FILTERS = 1
    private const val ACTION_SHOW_HELP = 2

    private val FILTER_DATE_FORMAT = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .toFormatter()
  }
}