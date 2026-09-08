const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

exports.monthlyRollover = functions.pubsub.schedule('1 0 1 * *')
  .timeZone('Asia/Kolkata') // Replace with your timezone if different
  .onRun(async (context) => {
    const db = admin.firestore();
    const customersRef = db.collection('customers');

    // Current month format 'YYYY-MM'
    const now = new Date();
    const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;

    console.log(`Running monthly rollover for ${currentMonth}`);

    try {
      // Fetch all customers where status is 'unpaid'
      const snapshot = await customersRef.where('status', '==', 'unpaid').get();
      
      if (snapshot.empty) {
        console.log('No unpaid customers found.');
        return null;
      }

      // Firestore batches can hold up to 500 operations
      const BATCH_SIZE = 500;
      const batches = [];
      let batch = db.batch();
      let operationCount = 0;
      let updateCount = 0;

      snapshot.forEach((doc) => {
        const data = doc.data();
        const lastPaidMonth = data.lastPaidMonth || '';

        // If lastPaidMonth is less than current month, apply rollover
        if (lastPaidMonth < currentMonth) {
          const baseAmount = data.baseAmount || 0;
          const pendingAmount = data.pendingAmount || 0;

          // Add baseAmount to pendingAmount
          batch.update(doc.ref, {
            pendingAmount: pendingAmount + baseAmount
            // Note: you may want to update lastPaidMonth to currentMonth or leave it as is
            // depending on your exact business logic. 
          });

          operationCount++;
          updateCount++;

          if (operationCount === BATCH_SIZE) {
            batches.push(batch.commit());
            batch = db.batch();
            operationCount = 0;
          }
        }
      });

      // Commit any remaining operations
      if (operationCount > 0) {
        batches.push(batch.commit());
      }

      await Promise.all(batches);
      console.log(`Successfully updated ${updateCount} unpaid customers.`);
      return null;
    } catch (error) {
      console.error('Error executing monthly rollover:', error);
      return null;
    }
  });
