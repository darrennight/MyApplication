package com.aviary.android.feather.async_tasks;

import it.sephiroth.android.library.media.ExifInterfaceExtended;
import android.os.Bundle;

import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.ThreadPoolService.BackgroundCallable;

public class ExifTask extends BackgroundCallable<String, Bundle> {

	@Override
	public Bundle call( IAviaryController context, String path ) {

		if ( path == null ) {
			return null;
		}

		Bundle result = new Bundle();

		try {
			ExifInterfaceExtended exif = new ExifInterfaceExtended( path );
			exif.copyTo( result );
		} catch ( Throwable t ) {
			t.printStackTrace();
		}
		return result;
	}

}
