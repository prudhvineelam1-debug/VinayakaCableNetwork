import android.widget.TextView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot

// Mock variables to satisfy IDE checks in this scratch file
val db = FirebaseFirestore.getInstance()
var countListener: ListenerRegistration? = null
val tvPaidCount: TextView? = null
val tvUnpaidCount: TextView? = null
val tvTotalCount: TextView? = null

countListener = db.collection("customers")
    .addSnapshotListener { snapshot: QuerySnapshot?, e: Exception? ->
        if (e != null || snapshot == null) return@addSnapshotListener

        var paidCount = 0
        var unpaidCount = 0

        for (doc in snapshot) {
            val status = doc.getString("status") ?: "unpaid"

            // ARCHITECT NOTE: We ignore 'Connection Status' here.
            // If they haven't paid, they are Unpaid, period.
            if (status.equals("paid", ignoreCase = true)) {
                paidCount++
            } else {
                unpaidCount++
            }
        }

        tvPaidCount?.text = paidCount.toString()
        tvUnpaidCount?.text = unpaidCount.toString()
        tvTotalCount?.text = snapshot.size().toString()
    }
