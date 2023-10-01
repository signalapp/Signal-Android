/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.glide.webp.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.signal.core.util.dp

/**
 * Main activity for this sample app.
 */
class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_activitiy)

    Glide.init(
      this,
      GlideBuilder()
        .setLogLevel(Log.VERBOSE)
    )

    val context = this

    findViewById<RecyclerView>(R.id.list).apply {
      adapter = ImageAdapter()
      layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
    }
  }

  class ImageAdapter : RecyclerView.Adapter<ImageViewHolder>() {

    private val data: List<String> = listOf(
      "test_01.webp",
      "test_02.webp",
      "test_03.webp",
      "test_04.webp",
      "test_05.webp",
      "test_06_lossless.webp",
      "test_06_lossy.webp",
      "test_07_lossless.webp",
      "test_09_large.webp"
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
      return ImageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.image_item, parent, false))
    }

    override fun getItemCount(): Int {
      return data.size
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
      holder.bind(data[position])
    }
  }

  class ImageViewHolder(itemView: View) : ViewHolder(itemView) {
    val image: ImageView by lazy { itemView.findViewById(R.id.image) }

    fun bind(filename: String) {
      Glide.with(itemView)
        .load(Uri.parse("file:///android_asset/$filename"))
        .skipMemoryCache(true)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .override(250.dp)
        .into(image)
    }
  }
}
