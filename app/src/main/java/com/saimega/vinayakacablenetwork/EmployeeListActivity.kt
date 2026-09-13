package com.saimega.vinayakacablenetwork

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class EmployeeListActivity : BaseActivity() {

    private lateinit var employeeListContainer: LinearLayout
    private lateinit var btnAddEmployee: MaterialButton
    private val authRepository = AuthRepository()
    private var currentUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employee_list)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        employeeListContainer = findViewById(R.id.employeeListContainer)
        btnAddEmployee = findViewById(R.id.btnAddEmployee)

        currentUsername = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).getString("username", "") ?: ""

        btnAddEmployee.setOnClickListener {
            showAddEmployeeDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        loadEmployees()
    }

    private fun loadEmployees() {
        lifecycleScope.launch {
            val employees = authRepository.listUsers()
            renderEmployeeList(employees)
        }
    }

    private fun renderEmployeeList(employees: List<UserAccount>) {
        employeeListContainer.removeAllViews()
        for (employee in employees) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_employee_row, employeeListContainer, false)
            row.findViewById<TextView>(R.id.tvEmployeeName).text = employee.name
            row.findViewById<TextView>(R.id.tvEmployeeUsernameRole).text = "${employee.username} · ${employee.role}"
            val tvStatus = row.findViewById<TextView>(R.id.tvEmployeeStatus)
            tvStatus.text = if (employee.active) getString(R.string.status_active) else getString(R.string.status_inactive)
            tvStatus.setTextColor(
                ContextCompat.getColor(this, if (employee.active) R.color.accent_green else R.color.accent_red)
            )
            row.setOnClickListener {
                showEditEmployeeDialog(employee)
            }
            employeeListContainer.addView(row)
        }
    }

    private fun showEditEmployeeDialog(employee: UserAccount) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_employee, null)
        val actvRole = dialogView.findViewById<AutoCompleteTextView>(R.id.actvRole)
        val switchActive = dialogView.findViewById<Switch>(R.id.switchActive)
        val tvSelfGuardNote = dialogView.findViewById<TextView>(R.id.tvSelfGuardNote)

        val roles = Roles.ALL
        actvRole.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, roles))
        actvRole.setText(employee.role, false)
        actvRole.setOnClickListener { actvRole.showDropDown() }
        switchActive.isChecked = employee.active

        val isSelf = employee.username == currentUsername
        if (isSelf) {
            switchActive.isEnabled = false
            tvSelfGuardNote.visibility = View.VISIBLE
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_employee)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val newRole = actvRole.text.toString().ifBlank { employee.role }
                val newActive = if (isSelf) true else switchActive.isChecked

                lifecycleScope.launch {
                    val success = authRepository.updateUserRoleAndActive(employee.username, newRole, newActive)
                    if (success) {
                        Toast.makeText(this@EmployeeListActivity, getString(R.string.employee_updated_successfully), Toast.LENGTH_SHORT).show()
                        loadEmployees()
                    }
                }
            }
            .show()
    }

    private fun showAddEmployeeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_employee, null)
        val etUsername = dialogView.findViewById<EditText>(R.id.etNewUsername)
        val etFullName = dialogView.findViewById<EditText>(R.id.etNewFullName)
        val etPassword = dialogView.findViewById<EditText>(R.id.etNewPassword)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.etNewConfirmPassword)
        val actvRole = dialogView.findViewById<AutoCompleteTextView>(R.id.actvNewRole)

        val roles = Roles.ALL
        actvRole.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, roles))
        actvRole.setText(Roles.EMPLOYEE, false)
        actvRole.setOnClickListener { actvRole.showDropDown() }

        AlertDialog.Builder(this)
            .setTitle(R.string.add_employee)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add_employee) { _, _ ->
                val username = etUsername.text.toString().trim()
                val name = etFullName.text.toString().trim()
                val password = etPassword.text.toString()
                val confirmPassword = etConfirmPassword.text.toString()
                val role = actvRole.text.toString().ifBlank { Roles.EMPLOYEE }

                if (username.isEmpty() || name.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, getString(R.string.please_enter_all_required_fields), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (password != confirmPassword) {
                    Toast.makeText(this, getString(R.string.passwords_do_not_match), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    when (authRepository.createEmployee(username, name, password, role)) {
                        is CreateEmployeeResult.Success -> {
                            Toast.makeText(this@EmployeeListActivity, getString(R.string.employee_added_successfully), Toast.LENGTH_SHORT).show()
                            loadEmployees()
                        }
                        is CreateEmployeeResult.UsernameAlreadyExists -> {
                            Toast.makeText(this@EmployeeListActivity, getString(R.string.username_already_exists), Toast.LENGTH_SHORT).show()
                        }
                        is CreateEmployeeResult.Failure -> {
                            Toast.makeText(this@EmployeeListActivity, getString(R.string.error_prefix, "unknown"), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .show()
    }
}
