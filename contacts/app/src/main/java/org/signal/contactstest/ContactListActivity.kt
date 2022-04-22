package org.signal.contactstest

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ContactListActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_contact_list)

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

    val viewModel: ContactListViewModel by viewModels()
    viewModel.contacts.observe(this) { adapter.submitList(it) }
  }
}
