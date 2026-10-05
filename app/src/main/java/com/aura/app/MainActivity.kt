package com.aura.app
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val requestCode = 9001
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    private fun render() {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,40,32,32); setBackgroundColor(getColor(com.aura.app.R.color.aura_bg)) }
        val title=TextView(this).apply { text="AURA\nCognitive Runtime"; textSize=30f; setTextColor(getColor(com.aura.app.R.color.aura_accent)) }
        status=TextView(this).apply { textSize=16f; setPadding(0,28,0,20); setTextColor(getColor(com.aura.app.R.color.aura_muted)) }
        val permissions=Button(this).apply { text="Aktifkan Camera + Microphone"; setOnClickListener { requestRuntimePermissions() } }
        root.addView(title); root.addView(status); root.addView(permissions); setContentView(ScrollView(this).apply { addView(root) }); updateStatus()
    }
    private fun updateStatus() {
        val camera=ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        val mic=ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
        status.text="AURA 1.6.4\n\nCore runtime: READY\nPolicy boundary: ACTIVE\nCamera permission: "+if(camera) "GRANTED" else "NOT GRANTED"+"\nMicrophone permission: "+if(mic) "GRANTED" else "NOT GRANTED"
    }
    private fun requestRuntimePermissions() {
        val missing=arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO).filter { ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED }.toTypedArray()
        if(missing.isNotEmpty()) ActivityCompat.requestPermissions(this,missing,requestCode) else updateStatus()
    }
}
