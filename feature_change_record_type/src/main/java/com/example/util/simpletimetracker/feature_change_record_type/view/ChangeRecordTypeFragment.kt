package com.example.util.simpletimetracker.feature_change_record_type.view

import android.os.Build
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.transition.TransitionInflater
import com.example.util.simpletimetracker.core.base.BaseFragment
import com.example.util.simpletimetracker.core.di.BaseViewModelFactory
import com.example.util.simpletimetracker.core.extension.hideKeyboard
import com.example.util.simpletimetracker.core.extension.observeOnce
import com.example.util.simpletimetracker.core.extension.rotateDown
import com.example.util.simpletimetracker.core.extension.rotateUp
import com.example.util.simpletimetracker.core.extension.setOnClick
import com.example.util.simpletimetracker.core.extension.showKeyboard
import com.example.util.simpletimetracker.core.extension.visible
import com.example.util.simpletimetracker.core.viewData.RecordTypeViewData
import com.example.util.simpletimetracker.domain.extension.orZero
import com.example.util.simpletimetracker.feature_change_record_type.R
import com.example.util.simpletimetracker.feature_change_record_type.adapter.ChangeRecordTypeAdapter
import com.example.util.simpletimetracker.feature_change_record_type.di.ChangeRecordTypeComponentProvider
import com.example.util.simpletimetracker.feature_change_record_type.extra.ChangeRecordTypeExtra
import com.example.util.simpletimetracker.feature_change_record_type.viewModel.ChangeRecordTypeViewModel
import com.example.util.simpletimetracker.navigation.params.ChangeRecordTypeParams
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import kotlinx.android.synthetic.main.change_record_type_fragment.*
import javax.inject.Inject

class ChangeRecordTypeFragment : BaseFragment(R.layout.change_record_type_fragment) {

    @Inject
    lateinit var viewModelFactory: BaseViewModelFactory<ChangeRecordTypeViewModel>

    private val viewModel: ChangeRecordTypeViewModel by viewModels(
        factoryProducer = { viewModelFactory }
    )
    private val colorsAdapter: ChangeRecordTypeAdapter by lazy {
        ChangeRecordTypeAdapter(viewModel::onColorClick, viewModel::onIconClick)
    }
    private val iconsAdapter: ChangeRecordTypeAdapter by lazy {
        ChangeRecordTypeAdapter(viewModel::onColorClick, viewModel::onIconClick)
    }
    private val typeId: Long by lazy { arguments?.getLong(ARGS_RECORD_ID).orZero() }

    override fun initDi() {
        (activity?.application as ChangeRecordTypeComponentProvider)
            .changeRecordTypeComponent
            ?.inject(this)
    }

    override fun initUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sharedElementEnterTransition = TransitionInflater.from(context)
                .inflateTransition(android.R.transition.move)
        }

        ViewCompat.setTransitionName(previewChangeRecordType, typeId.toString())

        rvChangeRecordTypeColor.apply {
            layoutManager = FlexboxLayoutManager(requireContext()).apply {
                flexDirection = FlexDirection.ROW
                justifyContent = JustifyContent.CENTER
                flexWrap = FlexWrap.WRAP
            }
            adapter = colorsAdapter
        }

        rvChangeRecordTypeIcon.apply {
            layoutManager = FlexboxLayoutManager(requireContext()).apply {
                flexDirection = FlexDirection.ROW
                justifyContent = JustifyContent.CENTER
                flexWrap = FlexWrap.WRAP
            }
            adapter = iconsAdapter
        }
    }

    override fun initUx() {
        etChangeRecordTypeName.doAfterTextChanged {
            viewModel.onNameChange(it.toString())
        }
        fieldChangeRecordTypeColor.setOnClick(viewModel::onColorChooserClick)
        fieldChangeRecordTypeIcon.setOnClick(viewModel::onIconChooserClick)
        btnChangeRecordTypeSave.setOnClick(viewModel::onSaveClick)
        btnChangeRecordTypeDelete.setOnClick(viewModel::onDeleteClick)
    }

    override fun initViewModel(): Unit = with(viewModel) {
        extra = ChangeRecordTypeExtra(
            id = typeId
        )
        deleteIconVisibility.observeOnce(
            viewLifecycleOwner, btnChangeRecordTypeDelete::visible::set
        )
        saveButtonEnabled.observe(viewLifecycleOwner, btnChangeRecordTypeSave::setEnabled)
        deleteButtonEnabled.observe(viewLifecycleOwner, btnChangeRecordTypeDelete::setEnabled)
        recordType.observeOnce(viewLifecycleOwner, ::updateUi)
        recordType.observe(viewLifecycleOwner, ::updatePreview)
        colors.observe(viewLifecycleOwner, colorsAdapter::replace)
        icons.observe(viewLifecycleOwner, iconsAdapter::replace)
        flipColorChooser.observe(viewLifecycleOwner) { opened ->
            rvChangeRecordTypeColor.visible = opened
            arrowChangeRecordTypeColor.apply {
                if (opened) rotateDown() else rotateUp()
            }
        }
        flipIconChooser.observe(viewLifecycleOwner) { opened ->
            rvChangeRecordTypeIcon.visible = opened
            arrowChangeRecordTypeIcon.apply {
                if (opened) rotateDown() else rotateUp()
            }
        }
        keyboardVisibility.observe(viewLifecycleOwner) { visible ->
            if (visible) showKeyboard(etChangeRecordTypeName) else hideKeyboard()
        }
    }

    private fun updateUi(item: RecordTypeViewData) {
        etChangeRecordTypeName.setText(item.name)
        etChangeRecordTypeName.setSelection(item.name.length)
    }

    private fun updatePreview(item: RecordTypeViewData) {
        with(previewChangeRecordType) {
            itemName = item.name
            itemIcon = item.iconId
            itemColor = item.color
        }
    }

    companion object {
        private const val ARGS_RECORD_ID = "record_id"

        fun createBundle(data: Any?): Bundle = Bundle().apply {
            when (data) {
                is ChangeRecordTypeParams -> putLong(ARGS_RECORD_ID, data.id)
            }
        }
    }
}