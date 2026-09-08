package com.saimega.vinayakacablenetwork

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ComplaintActivity : BaseActivity() {

    private lateinit var rvComplaints: RecyclerView
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: ComplaintAdapter
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complaint)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        rvComplaints = findViewById(R.id.rvComplaints)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)

        rvComplaints.layoutManager = LinearLayoutManager(this)
        adapter = ComplaintAdapter(emptyList()) { complaint ->
            // showComplaintDetails(complaint)
        }
        rvComplaints.adapter = adapter

        fetchComplaints()
    }

    private fun fetchComplaints() {
        progressBar.visibility = View.VISIBLE
        db.collection("complaints")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                progressBar.visibility = View.GONE
                if (e != null) return@addSnapshotListener

                val list = snapshot?.toObjects(ComplaintModel::class.java) ?: emptyList()
                adapter.updateList(list)

                if (list.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvComplaints.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rvComplaints.visibility = View.VISIBLE
                }
            }
    }
}
