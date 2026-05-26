package com.example.callback

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent

class NetworkJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        // Network is back! Show the button
        val serviceIntent = Intent(this, FloatingButtonService::class.java)
        startService(serviceIntent)
        return false // Job finished
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}