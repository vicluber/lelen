package com.lelen

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class LanguageSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_selection)

        val spinner: Spinner = findViewById(R.id.selectionSpinner)
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter.createFromResource(
            this,
            R.array.selection_options, // The array you defined in strings.xml
            android.R.layout.simple_spinner_item // Default spinner item layout
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spinner.adapter = adapter
        }

        // Assuming you have a Button with ID btnSelect in your layout
        val buttonSelect = findViewById<Button>(R.id.btnSelect)
        buttonSelect.setOnClickListener {
            val spinner = findViewById<Spinner>(R.id.selectionSpinner)
            val selectedValue = spinner.selectedItem.toString() // Get the selected item from the Spinner
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("selectedValue", selectedValue)
            startActivity(intent)
            finish() // Close SelectionActivity
        }
    }
}