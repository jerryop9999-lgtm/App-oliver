package com.oliver.multiapp

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

class ProvisioningActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == DevicePolicyManager.ACTION_GET_PROVISIONING_MODE) {
            setResult(
                RESULT_OK,
                Intent().putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                    DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
                )
            )
        } else {
            setResult(RESULT_OK)
        }
        finish()
    }
}
