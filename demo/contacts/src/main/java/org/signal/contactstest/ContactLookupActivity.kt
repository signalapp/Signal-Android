package org.signal.contactstest

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ContactLookupActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_contact_lookup)

    val list: RecyclerView = findViewById(R.id.list)
    val adapter = ContactsAdapter { uri ->
      startActivity(
        Intent(Intent.ACTION_VIEW).apply {
          data = uri
        }
      )
    }

    list.layoutManager = LinearLayoutManager(this)
    list.adapter = adapter

    val viewModel: ContactLookupViewModel by viewModels()
    viewModel.contacts.observe(this) { adapter.submitList(it) }

    val lookupText: TextView = findViewById(R.id.lookup_text)
    val lookupButton: Button = findViewById(R.id.lookup_button)

    lookupButton.setOnClickListener {
      viewModel.onLookup(lookupText.text.toString())
    }
  }
}
