package com.example.drivenext.presentation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.drivenext.databinding.ItemCarBinding
import com.example.drivenext.domain.Car

class CarAdapter(private val details: (Car)->Unit, private val rent: (Car)->Unit) : RecyclerView.Adapter<CarAdapter.Holder>() {
    private var items = emptyList<Car>()
    fun submit(value: List<Car>) { items=value; notifyDataSetChanged() }
    class Holder(val binding: ItemCarBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemCarBinding.inflate(LayoutInflater.from(parent.context),parent,false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val car=items[position]; val b=holder.binding
        b.carName.text="${car.brand} ${car.model}"
        b.carPrice.text="${car.price} ₽ / сутки"
        b.carSpecs.text="${car.year} • ${car.transmission} • ${car.fuel} • ${car.seats} мест"
        b.carDetails.setOnClickListener { details(car) }
        b.carRent.setOnClickListener { rent(car) }
    }
}
