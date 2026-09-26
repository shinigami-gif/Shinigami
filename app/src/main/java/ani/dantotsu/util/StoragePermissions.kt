package ani.dantotsu.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ani.dantotsu.R
import ani.dantotsu.toast

class StoragePermissions {
    companion object {
        fun downloadsPermission(activity: AppCompatActivity): Boolean {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) return true
            val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )

            val requiredPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            return if (requiredPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    activity,
                    requiredPermissions,
                    DOWNLOADS_PERMISSION_REQUEST_CODE
                )
                false
            } else {
                true
            }
        }

        private const val DOWNLOADS_PERMISSION_REQUEST_CODE = 100
    }
}

