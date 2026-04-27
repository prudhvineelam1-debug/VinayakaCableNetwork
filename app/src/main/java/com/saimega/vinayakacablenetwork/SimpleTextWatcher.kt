package com.saimega.vinayakacablenetwork

import android.text.Editable
import android.text.TextWatcher

class SimpleTextWatcher(val onChange: () -> Unit) : TextWatcher {
    override fun afterTextChanged(s: Editable?) = onChange()
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}