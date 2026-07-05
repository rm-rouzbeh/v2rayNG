package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLoginBinding
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.tonic.TonicManager
import com.v2ray.ang.tonic.TonicResult
import com.v2ray.ang.tonic.TonicStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TONIC login screen. Authenticates against the backend and, on success,
 * provisions the subscriptions and moves on to [TonicMainActivity].
 */
class LoginActivity : BaseActivity() {
    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already logged in -> skip straight to the main screen.
        if (TonicStore.isLoggedIn()) {
            goMain()
            return
        }

        setContentView(binding.root)
        binding.btnLogin.setOnClickListener { doLogin() }
    }

    private fun doLogin() {
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        if (username.isEmpty() || password.isEmpty()) {
            toastError(R.string.tonic_empty_credentials)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                TonicManager.login(username, password)
            }
            setLoading(false)
            when (result) {
                is TonicResult.Success -> goMain()
                is TonicResult.Error -> {
                    if (result.code == 401 || result.code == 403) {
                        toastError(R.string.tonic_login_failed)
                    } else {
                        toastError(R.string.tonic_network_error)
                    }
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.etUsername.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
    }

    private fun goMain() {
        startActivity(Intent(this, TonicMainActivity::class.java))
        finish()
    }
}
