/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.apng

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder

class DemoActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.demo_activity)

    val myAdapter = MyAdapter()

    findViewById<RecyclerView?>(R.id.list).apply {
      layoutManager = LinearLayoutManager(this@DemoActivity)
      adapter = myAdapter
    }

    // All test cases taken from:
    // https://philip.html5.org/tests/apng/tests.html
    myAdapter.submitList(
      listOf(
        TestModel.Heading("Basic cases"),
        // TODO we don't yet render non-apngs
//      TestModel(
//        filename = "test00.png",
//        description = "Trivial static image. This should be solid green."
//      ),
        TestModel.ApngModel(
          filename = "test01.png",
          description = "Trivial animated image - one frame; using default image. This should be solid green."
        ),
        TestModel.ApngModel(
          filename = "test02.png",
          description = "Trivial animated image - one frame; ignoring default image. This should be solid green."
        ),

        // IDAT, fdAT splitting
        // TODO we don't yet support split IDAT/fdAT
//      _root_ide_package_.org.signal.apng.MainActivity.TestModel.ApngModel(
//        filename = "test03.png",
//        description = "Basic split IDAT. This should be solid green."
//      ),
//      _root_ide_package_.org.signal.apng.MainActivity.TestModel.ApngModel(
//        filename = "test04.png",
//        description = "Split IDAT with zero-length chunk. This should be solid green."
//      ),
//      _root_ide_package_.org.signal.apng.MainActivity.TestModel.ApngModel(
//        filename = "test05.png",
//        description = "Basic split fdAT. This should be solid green."
//      ),
//      _root_ide_package_.org.signal.apng.MainActivity.TestModel.ApngModel(
//        filename = "test06.png",
//        description = "Split fdAT with zero-length chunk. This should be solid green."
//      ),

        TestModel.Heading("Dispose ops"),
        TestModel.ApngModel(
          filename = "test07.png",
          description = "APNG_DISPOSE_OP_NONE - basic. This should be solid green."
        ),
        TestModel.ApngModel(
          filename = "test08.png",
          description = "APNG_DISPOSE_OP_BACKGROUND - basic. This should be transparent."
        ),
        TestModel.ApngModel(
          filename = "test09.png",
          description = "APNG_DISPOSE_OP_BACKGROUND - final frame. This should be solid green."
        ),
        TestModel.ApngModel(
          filename = "test10.png",
          description = "APNG_DISPOSE_OP_PREVIOUS - basic. This should be solid green."
        ),
        TestModel.ApngModel(
          filename = "test11.png",
          description = "APNG_DISPOSE_OP_PREVIOUS - final frame. This should be solid green."
        ),
        TestModel.ApngModel(
          filename = "test12.png",
          description = "APNG_DISPOSE_OP_PREVIOUS - first frame. This should be transparent."
        ),

        TestModel.Heading("Dispose ops and regions"),
        TestModel.ApngModel(
          filename = "test13.png",
          description = "APNG_DISPOSE_OP_NONE in region. This should be solid green."
        ),
        TestModel.ApngModel(
          filename = "test14.png",
          description = "APNG_DISPOSE_OP_BACKGROUND before region. This should be transparent."
        ),
        TestModel.ApngModel(
          filename = "test15.png",
          description = "APNG_DISPOSE_OP_BACKGROUND in region. This should be a solid blue rectangle containing a smaller transparent rectangle."
        ),
        TestModel.ApngModel(
          filename = "test16.png",
          description = "APNG_DISPOSE_OP_PREVIOUS in region. This should be solid green."
        ),

        TestModel.Heading("Blend ops"),
        TestModel.ApngModel(
          filename = "test17.png",
          description = "APNG_BLEND_OP_SOURCE on solid colour. This should be solid green."
        ),
        TestModel.ApngModel(
          filename = "test18.png",
          description = "APNG_BLEND_OP_SOURCE on transparent colour. This should be transparent."
        ),
        TestModel.ApngModel(
          filename = "test19.png",
          description = "APNG_BLEND_OP_SOURCE on nearly-transparent colour. This should be very nearly transparent."
        ),
        TestModel.ApngModel(
          filename = "test20.png",
          description = "APNG_BLEND_OP_OVER on solid and transparent colours. This should be solid green. "
        ),
        TestModel.ApngModel(
          filename = "test21.png",
          description = "APNG_BLEND_OP_OVER repeatedly with nearly-transparent colours. This should be solid green."
        ),

        TestModel.Heading("Blending and gamma"),
        TestModel.ApngModel(
          filename = "test22.png",
          description = "APNG_BLEND_OP_OVER This should be solid slightly-dark green."
        ),
        TestModel.ApngModel(
          filename = "test23.png",
          description = "APNG_BLEND_OP_OVER This should be solid nearly-black."
        ),

        TestModel.Heading("Chunk ordering"),
        TestModel.ApngModel(
          filename = "test24.png",
          description = "fcTL before acTL. This should be solid green."
        ),

        TestModel.Heading("Delays"),
        TestModel.ApngModel(
          filename = "test25.png",
          description = "Basic delays. This should flash blue for half a second, then yellow for one second, then repeat."
        ),
        TestModel.ApngModel(
          filename = "test26.png",
          description = "Rounding of division. This should flash blue for half a second, then yellow for one second, then repeat."
        ),
        TestModel.ApngModel(
          filename = "test27.png",
          description = "16-bit numerator/denominator. This should flash blue for half a second, then yellow for one second, then repeat."
        ),
        TestModel.ApngModel(
          filename = "test28.png",
          description = "Zero denominator. This should flash blue for half a second, then yellow for one second, then repeat."
        ),
        TestModel.ApngModel(
          filename = "test29.png",
          description = "Zero numerator. This should flash cyan for a short period of time (perhaps zero), then magenta for the same short period of time, then blue for half a second, then yellow for one second, then repeat."
        ),

        TestModel.Heading("num_plays"),
        TestModel.ApngModel(
          filename = "test30.png",
          description = "num_plays = 0. This should flash yellow for one second, then blue for one second, then repeat forever."
        ),
        TestModel.ApngModel(
          filename = "test31.png",
          description = "num_plays = 1. When first loaded, this should flash yellow for one second, then stay blue forever."
        ),
        TestModel.ApngModel(
          filename = "test32.png",
          description = "num_plays = 2. When first loaded, this should flash yellow for one second, then blue for one second, then yellow for one second, then blue forever."
        ),

        TestModel.Heading("Other depths and color types"),
        TestModel.ApngModel(
          filename = "test33.png",
          description = "16-bit colour. This should be dark blue."
        ),
        TestModel.ApngModel(
          filename = "test34.png",
          description = "8-bit greyscale. This should be a solid grey rectangle containing a solid white rectangle."
        ),
        TestModel.ApngModel(
          filename = "test35.png",
          description = "8-bit greyscale and alpha, with blending. This should be solid grey."
        ),
        TestModel.ApngModel(
          filename = "test36.png",
          description = "2-color palette. This should be solid green."
        ),
        TestModel.ApngModel(
          filename = "test37.png",
          description = "2-bit palette and alpha. This should be solid green."
        ),
        TestModel.ApngModel(
          filename = "test38.png",
          description = "1-bit palette and alpha, with blending. This should be solid dark blue."
        ),

        TestModel.Heading("Random sample images"),
        TestModel.ApngModel(
          filename = "clock.png",
          description = "A clock that uses BLEND_OP_OVER to draw moving hands."
        ),
        TestModel.ApngModel(
          filename = "ball.png",
          description = "Classic bouncing ball."
        ),
        TestModel.ApngModel(
          filename = "elephant.png",
          description = "A cute elephant"
        )
      )
    )
  }

  sealed class TestModel {
    data class Heading(val description: String) : TestModel()
    data class ApngModel(val filename: String, val description: String) : TestModel()
  }

  private class MyAdapter : ListAdapter<TestModel, TestViewHolder>(object : DiffUtil.ItemCallback<TestModel>() {
    override fun areItemsTheSame(oldItem: TestModel, newItem: TestModel): Boolean {
      return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: TestModel, newItem: TestModel): Boolean {
      return oldItem == newItem
    }
  }) {
    override fun getItemViewType(position: Int): Int {
      return when (getItem(position)) {
        is TestModel.Heading -> 1
        is TestModel.ApngModel -> 2
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestViewHolder {
      when (viewType) {
        1 -> return TestViewHolder.HeadingViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_heading, parent, false))
        2 -> return TestViewHolder.ApngViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_apng, parent, false))
        else -> throw IllegalStateException()
      }
    }

    override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
      holder.bind(getItem(position))
    }
  }

  sealed class TestViewHolder(itemView: View) : ViewHolder(itemView) {
    abstract fun bind(testModel: TestModel)

    class HeadingViewHolder(itemView: View) : TestViewHolder(itemView) {
      val description: TextView = itemView.findViewById(R.id.description)

      override fun bind(testModel: TestModel) {
        if (testModel !is TestModel.Heading) {
          throw IllegalStateException()
        }

        description.text = testModel.description
      }
    }

    class ApngViewHolder(itemView: View) : TestViewHolder(itemView) {
      val description: TextView = itemView.findViewById(R.id.description)
      val image: ImageView = itemView.findViewById(R.id.image)

      override fun bind(testModel: TestModel) {
        if (testModel !is TestModel.ApngModel) {
          throw IllegalStateException()
        }

        description.text = testModel.description

        val decoder = ApngDecoder(itemView.context.assets.open(testModel.filename))
        val drawable = ApngDrawable(decoder)
        image.setImageDrawable(drawable)
      }
    }
  }
}
