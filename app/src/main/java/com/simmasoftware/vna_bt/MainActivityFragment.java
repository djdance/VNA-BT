package com.simmasoftware.vna_bt;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {

    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    public void update(Integer id, String label) {
        View v = getView();
        if (v != null) {
            TextView tv = ((TextView) v.findViewById(id));
            if (tv != null) {
                tv.setText(label);
            }
        }
    }
}
