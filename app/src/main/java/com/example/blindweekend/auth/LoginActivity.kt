package com.example.blindweekend.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.blindweekend.R
import com.example.blindweekend.network.RetrofitClient
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * 登录/注册页面
 *
 * 功能：
 * - Tab 切换：登录 / 注册
 * - 登录：手机号 + 密码 → 调用API登录
 * - 注册：手机号 + 昵称 + 密码 → 调用API注册并自动登录
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var tabAuthMode: TabLayout
    private lateinit var layoutLogin: View
    private lateinit var layoutRegister: View
    private lateinit var progressAuth: ProgressBar

    // 登录输入框
    private lateinit var etLoginPhone: TextInputEditText
    private lateinit var etLoginPassword: TextInputEditText

    // 注册输入框
    private lateinit var etRegPhone: TextInputEditText
    private lateinit var etRegNickname: TextInputEditText
    private lateinit var etRegPassword: TextInputEditText
    private lateinit var etRegPasswordConfirm: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 如果已登录，直接返回
        if (AuthManager.isLoggedIn) {
            finish()
            return
        }

        initViews()
        setupTabs()
        setupButtons()
        prefillPhone()
    }

    private fun initViews() {
        tabAuthMode = findViewById(R.id.tab_auth_mode)
        layoutLogin = findViewById(R.id.layout_login)
        layoutRegister = findViewById(R.id.layout_register)
        progressAuth = findViewById(R.id.progress_auth)

        etLoginPhone = findViewById(R.id.et_login_phone)
        etLoginPassword = findViewById(R.id.et_login_password)

        etRegPhone = findViewById(R.id.et_reg_phone)
        etRegNickname = findViewById(R.id.et_reg_nickname)
        etRegPassword = findViewById(R.id.et_reg_password)
        etRegPasswordConfirm = findViewById(R.id.et_reg_password_confirm)
    }

    /**
     * 设置 Tab 切换（登录 ↔ 注册）
     */
    private fun setupTabs() {
        // 动态添加Tab
        tabAuthMode.addTab(tabAuthMode.newTab().setText("登录"))
        tabAuthMode.addTab(tabAuthMode.newTab().setText("注册"))

        tabAuthMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> { layoutLogin.visibility = View.VISIBLE; layoutRegister.visibility = View.GONE }
                    1 -> { layoutLogin.visibility = View.GONE; layoutRegister.visibility = View.VISIBLE }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    /**
     * 设置按钮点击事件
     */
    private fun setupButtons() {
        // 登录按钮
        findViewById<View>(R.id.btn_login).setOnClickListener {
            doLogin()
        }
        // 注册按钮
        findViewById<View>(R.id.btn_register).setOnClickListener {
            doRegister()
        }
    }

    /** 预填手机号（如果有保存的）*/
    private fun prefillPhone() {
        val savedPhone = AuthManager.savedPhone
        if (savedPhone.isNotEmpty()) {
            etLoginPhone.setText(savedPhone)
            etRegPhone.setText(savedPhone)
        }
    }

    // ==================== 登录逻辑 ====================

    private fun doLogin() {
        val phone = etLoginPhone.text.toString().trim()
        val password = etLoginPassword.text.toString().trim()

        // 输入校验
        when {
            phone.isEmpty() -> showToast("请输入手机号")
            !isValidPhone(phone) -> showToast("请输入正确的11位手机号")
            password.isEmpty() -> showToast("请输入密码")
            password.length < 4 -> showToast("密码至少4位")
            else -> performLogin(phone, password)
        }
    }

    private fun performLogin(phone: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.login(
                    mapOf("phone" to phone, "password" to password)
                )
                if (response.isSuccessful && response.body()?.code == 200) {
                    val user = response.body()?.data
                    if (user != null) {
                        AuthManager.saveLoginInfo(
                            token = "token_${user.id}_${System.currentTimeMillis()}",
                            user = user
                        )
                        AuthManager.savedPhone = phone
                        showToast("登录成功！欢迎回来，${user.nickname ?: "探索者"}")
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        showToast(response.body()?.message ?: "登录失败，请重试")
                    }
                } else {
                    val msg = response.body()?.message ?: "登录失败"
                    showToast(msg)
                }
            } catch (e: Exception) {
                showToast("网络错误：${e.message}")
            }
            setLoading(false)
        }
    }

    // ==================== 注册逻辑 ====================

    private fun doRegister() {
        val phone = etRegPhone.text.toString().trim()
        val nickname = etRegNickname.text.toString().trim()
        val password = etRegPassword.text.toString().trim()
        val passwordConfirm = etRegPasswordConfirm.text.toString().trim()

        // 输入校验
        when {
            phone.isEmpty() -> showToast("请输入手机号")
            !isValidPhone(phone) -> showToast("请输入正确的11位手机号")
            nickname.isEmpty() -> showToast("请输入昵称")
            nickname.length < 2 || nickname.length > 12 -> showToast("昵称需要2-12个字符")
            password.isEmpty() -> showToast("请输入密码")
            password.length < 6 -> showToast("密码至少6位")
            password != passwordConfirm -> showToast("两次输入的密码不一致")
            else -> performRegister(phone, nickname, password)
        }
    }

    private fun performRegister(phone: String, nickname: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.register(
                    mapOf(
                        "phone" to phone,
                        "nickname" to nickname,
                        "password" to password
                    )
                )
                if (response.isSuccessful && response.body()?.code == 200) {
                    val user = response.body()?.data
                    if (user != null) {
                        AuthManager.saveLoginInfo(
                            token = "token_${user.id}_${System.currentTimeMillis()}",
                            user = user
                        )
                        AuthManager.savedPhone = phone
                        showToast("注册成功！欢迎加入不期周末")
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        showToast(response.body()?.message ?: "注册失败，请重试")
                    }
                } else {
                    val msg = response.body()?.message ?: "注册失败"
                    // 显示更友好的错误信息
                    showToast(msg)
                }
            } catch (e: Exception) {
                showToast("网络错误：${e.message}")
            }
            setLoading(false)
        }
    }

    // ==================== 工具方法 ====================

    private fun setLoading(loading: Boolean) {
        progressAuth.visibility = if (loading) View.VISIBLE else View.GONE
        // 禁用/启用输入框
        setInputsEnabled(!loading)
    }

    private fun setInputsEnabled(enabled: Boolean) {
        etLoginPhone.isEnabled = enabled
        etLoginPassword.isEnabled = enabled
        etRegPhone.isEnabled = enabled
        etRegNickname.isEnabled = enabled
        etRegPassword.isEnabled = enabled
        etRegPasswordConfirm.isEnabled = enabled
        findViewById<View>(R.id.btn_login)?.isEnabled = enabled
        findViewById<View>(R.id.btn_register)?.isEnabled = enabled
    }

    private fun isValidPhone(phone: String): Boolean {
        return Pattern.matches("^1[3-9]\\d{9}$", phone)
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val REQUEST_CODE_LOGIN = 1001
        const val RESULT_LOGIN_SUCCESS = 2001
    }
}
