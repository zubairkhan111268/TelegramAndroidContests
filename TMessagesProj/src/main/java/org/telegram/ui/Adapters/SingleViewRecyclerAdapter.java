package org.telegram.ui.Adapters;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.ui.Components.RecyclerListView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SingleViewRecyclerAdapter extends RecyclerListView.SelectionAdapter{

	private View view;
	private int id;

	private static int nextId=1;

	public SingleViewRecyclerAdapter(View view){
		this.view=view;
		if(Build.VERSION.SDK_INT<17)
			id=nextId++;
		else
			id=View.generateViewId();
	}

	@NonNull
	@Override
	public ViewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
		return new ViewViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position){

	}

	@Override
	public int getItemCount(){
		return 1;
	}

	@Override
	public int getItemViewType(int position){
		return id;
	}

	@Override
	public boolean isEnabled(RecyclerView.ViewHolder holder){
		return false;
	}

	public class ViewViewHolder extends RecyclerView.ViewHolder{
		public ViewViewHolder(@NonNull View itemView){
			super(itemView);
		}
	}
}
