package com.example.util.simpletimetracker.feature_change_record.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.simpletimetracker.core.extension.addOrRemove
import com.example.util.simpletimetracker.core.extension.set
import com.example.util.simpletimetracker.core.extension.toParams
import com.example.util.simpletimetracker.core.interactor.RecordTagViewDataInteractor
import com.example.util.simpletimetracker.core.interactor.RecordTypesViewDataInteractor
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.core.view.timeAdjustment.TimeAdjustmentView
import com.example.util.simpletimetracker.domain.extension.flip
import com.example.util.simpletimetracker.domain.extension.orFalse
import com.example.util.simpletimetracker.domain.interactor.AddRecordMediator
import com.example.util.simpletimetracker.domain.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.model.Record
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_base_adapter.category.CategoryViewData
import com.example.util.simpletimetracker.feature_base_adapter.recordType.RecordTypeViewData
import com.example.util.simpletimetracker.feature_change_record.R
import com.example.util.simpletimetracker.feature_change_record.interactor.ChangeRecordViewDataInteractor
import com.example.util.simpletimetracker.feature_change_record.viewData.ChangeRecordChooserState
import com.example.util.simpletimetracker.feature_change_record.viewData.ChangeRecordCommentViewData
import com.example.util.simpletimetracker.feature_change_record.viewData.TimeAdjustmentState
import com.example.util.simpletimetracker.navigation.Router
import com.example.util.simpletimetracker.navigation.params.notification.ToastParams
import com.example.util.simpletimetracker.navigation.params.screen.ChangeRecordTagFromScreen
import com.example.util.simpletimetracker.navigation.params.screen.ChangeTagData
import com.example.util.simpletimetracker.navigation.params.screen.DateTimeDialogParams
import com.example.util.simpletimetracker.navigation.params.screen.DateTimeDialogType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

abstract class ChangeRecordBaseViewModel(
    private val router: Router,
    private val resourceRepo: ResourceRepo,
    private val prefsInteractor: PrefsInteractor,
    private val recordTypesViewDataInteractor: RecordTypesViewDataInteractor,
    private val recordTagViewDataInteractor: RecordTagViewDataInteractor,
    private val changeRecordViewDataInteractor: ChangeRecordViewDataInteractor,
    private val addRecordMediator: AddRecordMediator,
    private val recordInteractor: RecordInteractor,
    private val changeRecordMergeDelegate: ChangeRecordMergeDelegateImpl,
) : ViewModel(),
    ChangeRecordMergeDelegate by changeRecordMergeDelegate {

    val types: LiveData<List<ViewHolderType>> by lazy {
        return@lazy MutableLiveData<List<ViewHolderType>>().let { initial ->
            viewModelScope.launch { initial.value = loadTypesViewData() }
            initial
        }
    }
    val categories: LiveData<List<ViewHolderType>> by lazy {
        return@lazy MutableLiveData<List<ViewHolderType>>().let { initial ->
            viewModelScope.launch {
                initializePreviewViewData()
                initial.value = loadCategoriesViewData()
            }
            initial
        }
    }
    val lastComments: LiveData<List<ViewHolderType>> by lazy {
        return@lazy MutableLiveData<List<ViewHolderType>>().let { initial ->
            viewModelScope.launch {
                initializePreviewViewData()
                initial.value = loadLastCommentsViewData()
            }
            initial
        }
    }
    val timeAdjustmentItems: LiveData<List<ViewHolderType>> by lazy {
        MutableLiveData(loadTimeAdjustmentItems())
    }
    val timeSplitAdjustmentItems: LiveData<List<ViewHolderType>> by lazy {
        MutableLiveData(loadTimeSplitAdjustmentItems())
    }
    val chooserState: LiveData<ChangeRecordChooserState> = MutableLiveData(
        ChangeRecordChooserState(
            current = ChangeRecordChooserState.State.Closed,
            previous = ChangeRecordChooserState.State.Closed,
        )
    )
    val timeAdjustmentState: LiveData<TimeAdjustmentState> = MutableLiveData(TimeAdjustmentState.HIDDEN)
    val timeSplitAdjustmentState: LiveData<Boolean> = MutableLiveData(false)
    val timeSplitText: LiveData<String> = MutableLiveData()
    val saveButtonEnabled: LiveData<Boolean> = MutableLiveData(true)
    val keyboardVisibility: LiveData<Boolean> = MutableLiveData(false)
    val comment: LiveData<String> = MutableLiveData()

    protected var newTypeId: Long = 0
    protected var newTimeEnded: Long = 0
    protected var newTimeStarted: Long = 0
    protected var newTimeSplit: Long = 0
    protected var newComment: String = ""
    protected var newCategoryIds: MutableList<Long> = mutableListOf()
    protected var originalTimeStarted: Long = 0
    protected var originalTimeEnded: Long = 0
    protected var prevRecord: Record? = null
    protected var nextRecord: Record? = null

    protected abstract suspend fun updatePreview()
    protected abstract fun getChangeCategoryParams(data: ChangeTagData): ChangeRecordTagFromScreen
    protected abstract fun onTimeSplitChanged()
    protected abstract fun onTimeStartedChanged()
    protected abstract fun onTimeEndedChanged()
    protected abstract suspend fun onSaveClickDelegate()
    protected abstract suspend fun onSplitClickDelegate()
    protected abstract suspend fun onAdjustClickDelegate()
    protected open suspend fun onContinueClickDelegate() {}
    protected abstract val mergeAvailable: Boolean

    protected open suspend fun initializePreviewViewData() {
        initializePrevNextRecords()
        updateTimeSplitValue()
        changeRecordMergeDelegate.updateMergePreviewViewData(
            mergeAvailable = mergeAvailable,
            prevRecord = prevRecord,
            newTimeEnded = newTimeEnded,
        )
    }

    protected suspend fun adjustPrevRecord() {
        prevRecord
            ?.let {
                it.copy(
                    timeStarted = it.timeStarted.coerceAtMost(newTimeStarted),
                    timeEnded = newTimeStarted,
                )
            }?.let {
                addRecordMediator.add(it)
            }
    }

    protected suspend fun adjustNextRecord() {
        nextRecord
            ?.let {
                it.copy(
                    timeStarted = newTimeEnded,
                    timeEnded = it.timeEnded.coerceAtLeast(newTimeEnded)
                )
            }?.let {
                addRecordMediator.add(it)
            }
    }

    fun onTypeChooserClick() {
        onNewChooserState(ChangeRecordChooserState.State.Activity)
    }

    fun onCategoryChooserClick() {
        onNewChooserState(ChangeRecordChooserState.State.Tag)
    }

    fun onCommentChooserClick() {
        onNewChooserState(ChangeRecordChooserState.State.Comment)
    }

    fun onActionChooserClick() {
        onNewChooserState(ChangeRecordChooserState.State.Action)
    }

    fun onTimeStartedClick() {
        onTimeClick(tag = TIME_STARTED_TAG, timestamp = newTimeStarted)
    }

    fun onTimeEndedClick() {
        onTimeClick(tag = TIME_ENDED_TAG, timestamp = newTimeEnded)
    }

    fun onTimeSplitClick() {
        onTimeClick(tag = TIME_SPLIT_TAG, timestamp = newTimeSplit)
    }

    fun onSaveClick() {
        onRecordChangeButtonClick(
            onProceed = ::onSaveClickDelegate,
        )
    }

    fun onSplitClick() {
        onRecordChangeButtonClick(
            onProceed = ::onSplitClickDelegate,
        )
    }

    fun onAdjustClick() {
        onRecordChangeButtonClick(
            onProceed = ::onAdjustClickDelegate,
        )
    }

    fun onContinueClick() {
        // Can't continue future record
        if (newTimeStarted > System.currentTimeMillis()) {
            showMessage(R.string.cannot_be_in_the_future)
            return
        }
        onRecordChangeButtonClick(
            onProceed = ::onContinueClickDelegate,
        )
    }

    fun onMergeClick() {
        onRecordChangeButtonClick(
            onProceed = {
                onMergeClickDelegate(
                    prevRecord = prevRecord,
                    newTimeEnded = newTimeEnded,
                    onMergeComplete = {
                        router.back()
                    }
                )
            },
            checkTypeSelected = false,
        )
    }

    fun onTypeClick(item: RecordTypeViewData) {
        viewModelScope.launch {
            if (item.id != newTypeId) {
                newTypeId = item.id
                newCategoryIds.clear()
                updatePreview()
                updateCategoriesViewData()
                updateLastCommentsViewData()
            }
        }
        onTypeChooserClick()
    }

    fun onCategoryClick(item: CategoryViewData) {
        viewModelScope.launch {
            when (item) {
                is CategoryViewData.Record.Tagged -> {
                    newCategoryIds.addOrRemove(item.id)
                }
                is CategoryViewData.Record.Untagged -> {
                    newCategoryIds.clear()
                }
                else -> return@launch
            }
            updatePreview()
            updateCategoriesViewData()
        }
    }

    fun onCategoryLongClick(item: CategoryViewData, sharedElements: Pair<Any, String>) {
        val icon = (item as? CategoryViewData.Record)?.icon?.toParams()

        router.navigate(
            data = getChangeCategoryParams(
                ChangeTagData.Change(
                    transitionName = sharedElements.second,
                    id = item.id,
                    preview = ChangeTagData.Change.Preview(
                        name = item.name,
                        color = item.color,
                        icon = icon,
                    )
                )
            ),
            sharedElements = mapOf(sharedElements)
        )
    }

    fun onAddCategoryClick() {
        val preselectedTypeId: Long? = newTypeId.takeUnless { it == 0L }
        router.navigate(
            data = getChangeCategoryParams(
                ChangeTagData.New(preselectedTypeId)
            )
        )
    }

    fun onCommentClick(item: ChangeRecordCommentViewData) {
        comment.set(item.text)
    }

    fun onCommentChange(comment: String) {
        viewModelScope.launch {
            if (comment != newComment) {
                newComment = comment
                updatePreview()
            }
        }
    }

    fun onDateTimeSet(timestamp: Long, tag: String?) {
        viewModelScope.launch {
            when (tag) {
                TIME_STARTED_TAG -> {
                    if (timestamp != newTimeStarted) {
                        newTimeStarted = timestamp
                        onTimeStartedChanged()
                        updatePreview()
                        updateTimeSplitValue()
                    }
                }
                TIME_ENDED_TAG -> {
                    if (timestamp != newTimeEnded) {
                        newTimeEnded = timestamp
                        onTimeEndedChanged()
                        updatePreview()
                        updateTimeSplitValue()
                    }
                }
                TIME_SPLIT_TAG -> {
                    if (timestamp != newTimeSplit) {
                        newTimeSplit = timestamp
                        onTimeSplitChanged()
                        updateTimeSplitValue()
                    }
                }
            }
        }
    }

    fun onAdjustTimeStartedClick() {
        updateAdjustTimeState(
            clicked = TimeAdjustmentState.TIME_STARTED,
            other = TimeAdjustmentState.TIME_ENDED
        )
    }

    fun onAdjustTimeEndedClick() {
        updateAdjustTimeState(
            clicked = TimeAdjustmentState.TIME_ENDED,
            other = TimeAdjustmentState.TIME_STARTED
        )
    }

    fun onAdjustTimeSplitClick() {
        val newValue = timeSplitAdjustmentState.value?.flip().orFalse()
        timeSplitAdjustmentState.set(newValue)
    }

    fun onAdjustTimeItemClick(viewData: TimeAdjustmentView.ViewData) {
        viewModelScope.launch {
            when (viewData) {
                is TimeAdjustmentView.ViewData.Now -> onAdjustTimeNowClick()
                is TimeAdjustmentView.ViewData.Adjust -> adjustRecordTime(viewData.value)
            }
            updatePreview()
            updateTimeSplitValue()
        }
    }

    fun onAdjustTimeSplitItemClick(viewData: TimeAdjustmentView.ViewData) {
        viewModelScope.launch {
            when (viewData) {
                is TimeAdjustmentView.ViewData.Now -> {
                    newTimeSplit = System.currentTimeMillis()
                    onTimeSplitChanged()
                }
                is TimeAdjustmentView.ViewData.Adjust -> {
                    newTimeSplit += TimeUnit.MINUTES.toMillis(viewData.value)
                    onTimeSplitChanged()
                }
            }
            updateTimeSplitValue()
        }
    }

    private fun onRecordChangeButtonClick(
        onProceed: suspend () -> Unit,
        checkTypeSelected: Boolean = true,
    ) {
        if (checkTypeSelected && newTypeId == 0L) {
            showMessage(R.string.change_record_message_choose_type)
            return
        }
        viewModelScope.launch {
            saveButtonEnabled.set(false)
            onProceed()
        }
    }

    private fun onNewChooserState(
        state: ChangeRecordChooserState.State,
    ) {
        val current = chooserState.value?.current ?: ChangeRecordChooserState.State.Closed
        val newState = if (current == state) {
            ChangeRecordChooserState.State.Closed
        } else {
            state
        }

        // Show keyboard on comment chooser opened, hide otherwise.
        keyboardVisibility.set(newState is ChangeRecordChooserState.State.Comment)
        timeAdjustmentState.set(TimeAdjustmentState.HIDDEN)
        chooserState.set(
            ChangeRecordChooserState(
                current = newState,
                previous = current,
            )
        )
    }

    private fun onTimeClick(
        tag: String,
        timestamp: Long,
    ) = viewModelScope.launch {
        val useMilitaryTime = prefsInteractor.getUseMilitaryTimeFormat()
        val firstDayOfWeek = prefsInteractor.getFirstDayOfWeek()
        val showSeconds = prefsInteractor.getShowSeconds()

        router.navigate(
            DateTimeDialogParams(
                tag = tag,
                timestamp = timestamp,
                type = DateTimeDialogType.DATETIME(),
                useMilitaryTime = useMilitaryTime,
                firstDayOfWeek = firstDayOfWeek,
                showSeconds = showSeconds,
            )
        )
    }

    protected fun showMessage(stringResId: Int) {
        val params = ToastParams(message = resourceRepo.getString(stringResId))
        router.show(params)
    }

    private fun onAdjustTimeNowClick() {
        when (timeAdjustmentState.value) {
            TimeAdjustmentState.TIME_STARTED -> {
                newTimeStarted = System.currentTimeMillis()
                onTimeStartedChanged()
            }
            TimeAdjustmentState.TIME_ENDED -> {
                newTimeEnded = System.currentTimeMillis()
                onTimeEndedChanged()
            }
            else -> {
                // Do nothing, it's hidden.
            }
        }
    }

    private fun adjustRecordTime(shiftInMinutes: Long) {
        when (timeAdjustmentState.value) {
            TimeAdjustmentState.TIME_STARTED -> {
                newTimeStarted += TimeUnit.MINUTES.toMillis(shiftInMinutes)
                onTimeStartedChanged()
            }
            TimeAdjustmentState.TIME_ENDED -> {
                newTimeEnded += TimeUnit.MINUTES.toMillis(shiftInMinutes)
                onTimeEndedChanged()
            }
            else -> {
                // Do nothing, it's hidden.
            }
        }
    }

    private fun updateAdjustTimeState(
        clicked: TimeAdjustmentState,
        other: TimeAdjustmentState,
    ) {
        when (timeAdjustmentState.value) {
            TimeAdjustmentState.HIDDEN -> {
                timeAdjustmentState.set(clicked)
            }
            clicked -> {
                timeAdjustmentState.set(TimeAdjustmentState.HIDDEN)
            }
            other -> viewModelScope.launch {
                timeAdjustmentState.set(TimeAdjustmentState.HIDDEN)
                delay(300)
                timeAdjustmentState.set(clicked)
            }
            else -> {
                // Do nothing
            }
        }
    }

    private suspend fun initializePrevNextRecords() {
        // TODO get directly from room
        val records = recordInteractor.getAll()
        prevRecord = records
            .sortedByDescending { it.timeEnded }
            .firstOrNull { it.timeEnded <= originalTimeStarted }
        nextRecord = records
            .sortedByDescending { it.timeStarted }
            .lastOrNull { it.timeStarted >= originalTimeEnded }
    }

    private suspend fun loadTypesViewData(): List<ViewHolderType> {
        return recordTypesViewDataInteractor.getTypesViewData()
    }

    protected fun updateCategoriesViewData() = viewModelScope.launch {
        val data = loadCategoriesViewData()
        categories.set(data)
    }

    private suspend fun loadCategoriesViewData(): List<ViewHolderType> {
        return recordTagViewDataInteractor.getViewData(
            selectedTags = newCategoryIds,
            typeId = newTypeId,
            multipleChoiceAvailable = true,
            showHint = true,
            showAddButton = true,
        )
    }

    private fun loadTimeAdjustmentItems(): List<ViewHolderType> {
        return changeRecordViewDataInteractor.getTimeAdjustmentItems()
    }

    private fun loadTimeSplitAdjustmentItems(): List<ViewHolderType> {
        return changeRecordViewDataInteractor.getTimeAdjustmentItems()
    }

    private suspend fun updateTimeSplitValue() {
        val data = loadTimeSplitValue()
        timeSplitText.set(data)
    }

    private suspend fun loadTimeSplitValue(): String {
        return changeRecordViewDataInteractor.mapTime(newTimeSplit)
    }

    private fun updateLastCommentsViewData() = viewModelScope.launch {
        val data = loadLastCommentsViewData()
        lastComments.set(data)
    }

    private suspend fun loadLastCommentsViewData(): List<ViewHolderType> {
        return changeRecordViewDataInteractor.getLastCommentsViewData(newTypeId)
    }

    companion object {
        const val TIME_STARTED_TAG = "time_started_tag"
        const val TIME_ENDED_TAG = "time_ended_tag"
        const val TIME_SPLIT_TAG = "time_split_tag"
    }
}