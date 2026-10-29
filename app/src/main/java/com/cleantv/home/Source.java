package com.cleantv.home;

import android.content.Context;

/** One kind of card. build(c, false) must be cheap: it reads only saved data and the device. */
interface Source {
    String id();
    int titleRes();
    int accent();
    Card build(Context c, boolean fetch) throws Exception;
}
