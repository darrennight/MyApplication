package com.aviary.android.feather.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Pair;

import com.aviary.android.feather.R;
import com.aviary.android.feather.headless.filters.INativeFilter;
import com.aviary.android.feather.headless.filters.NativeFilterProxy;
import com.aviary.android.feather.headless.filters.impl.EffectFilter;
import com.aviary.android.feather.headless.moa.MoaAction;
import com.aviary.android.feather.headless.moa.MoaActionFactory;
import com.aviary.android.feather.headless.moa.MoaActionList;
import com.aviary.android.feather.headless.moa.MoaResult;
import com.aviary.android.feather.library.content.FeatherIntent;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.FilterLoaderFactory;
import com.aviary.android.feather.library.filters.FilterLoaderFactory.Filters;
import com.aviary.android.feather.library.plugins.PluginFactory.ICDSPlugin;
import com.aviary.android.feather.library.plugins.PluginFactory.InternalPlugin;
import com.aviary.android.feather.library.services.CDSPackage.CDSEntry;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.ImageCacheService;
import com.aviary.android.feather.library.services.PluginService;
import com.aviary.android.feather.library.threading.Future;
import com.aviary.android.feather.library.threading.FutureListener;
import com.aviary.android.feather.library.utils.BitmapUtils;

public class EffectsPanel extends BordersPanel {

	private int mThumbPadding;
	private int mThumbRoundedCorners;
	private int mThumbStrokeColor;
	private int mThumbStrokeWidth;	

	public EffectsPanel( IAviaryController context, ToolEntry entry ) {
		super( context, entry, FeatherIntent.PluginType.TYPE_FILTER );
	}

	@Override
	public void onCreate( Bitmap bitmap, Bundle options ) {
		super.onCreate( bitmap, options );
		
		mLogger.info( "FastPreview enabled: "+  mEnableFastPreview );

		mThumbPadding = mConfigService.getDimensionPixelSize( R.dimen.aviary_effect_thumb_padding );
		mThumbRoundedCorners = mConfigService.getDimensionPixelSize( R.dimen.aviary_effect_thumb_radius );
		mThumbStrokeWidth = mConfigService.getDimensionPixelSize( R.dimen.aviary_effect_thumb_stroke );
		mThumbStrokeColor = mConfigService.getColor( R.color.aviary_effect_thumb_stroke_color );
	}

	@Override
	protected void onDispose() {
		super.onDispose();
	}

	@Override
	protected void onProgressEnd() {
		if ( !mEnableFastPreview ) {
			super.onProgressModalEnd();
		} else {
			super.onProgressEnd();
		}
	}

	@Override
	protected void onProgressStart() {
		if ( !mEnableFastPreview ) {
			super.onProgressModalStart();
		} else {
			super.onProgressStart();
		}
	}
	
	@Override
	protected ListAdapter createListAdapter( Context context, List<EffectPack> result ) {
		return new EffectsListAdapter( context, 
				R.layout.aviary_frame_item, 
				R.layout.aviary_effect_item_more, 
				R.layout.aviary_frame_item_external, 
				R.layout.aviary_frame_item_divider, result );
	}	
	
	@Override
	protected RenderTask createRenderTask( int position ) {
		return new EffectsRenderTask( position );
	}
	
	@Override
	protected INativeFilter loadNativeFilter( final EffectPack pack, int position, final CharSequence name, boolean hires ) {
		EffectFilter filter = (EffectFilter) FilterLoaderFactory.get( Filters.EFFECTS );
		
		long effectId = pack.getItemIdAt( position );
		if( !mCDSService.opened() ) mCDSService.open();
		
		mLogger.log( "loading filter: " + effectId );
		
		byte[] content = mCDSService.loadEntryContent( effectId );
		if( null != content ) {
			String contentString = new String( content );
			filter.setMoaLiteEffectContent( contentString );
		}
		
		filter.setEffectName( name );
		return filter;
	}
	
	@Override
	protected CharSequence[] getOptionalEffectsLabels() {
		return super.getOptionalEffectsLabels();
	}
	
	@Override
	protected CharSequence[] getOptionalEffectsValues() {
		return new CharSequence[] { "-1" };
	}
	
	@Override
	protected List<Long> loadPluginIds( InternalPlugin plugin ) {
		List<Long> result = null;
		if( plugin instanceof ICDSPlugin ) {
			result = new ArrayList<Long>();
			for( int i = 0; i < plugin.size(); i++ ) {
				CDSEntry item = ( (ICDSPlugin) plugin ).getItemAt( i );
				result.add( item.getId() );
			}
		}
		return result;
	}
	
	@Override
	protected List<Pair<String, String>> loadPluginItems( InternalPlugin plugin ) {
		List<Pair<String, String>> result = new ArrayList<Pair<String,String>>();
		
		if( plugin instanceof ICDSPlugin ) {
			for( int i = 0; i < plugin.size(); i++ ) {
				CDSEntry item = ( (ICDSPlugin) plugin ).getItemAt( i );
				result.add( Pair.create( String.valueOf( item.getIdentifier() ), item.getDisplayName() ) );
			}
		}
		return result;		
	}

	
	protected class EffectsRenderTask extends RenderTask {
		
		private Object mOpenGlCompleted = new Object();
		
		FutureListener<Boolean> mOpenGlBackgroundListener = new FutureListener<Boolean>() {

			@Override
			public void onFutureDone( Future<Boolean> arg0 ) {
				mLogger.info( "mOpenGlBackgroundListener::onFutureDone" );
				synchronized ( mOpenGlCompleted ) {
					mOpenGlCompleted.notify();
				}
			}
		};		
		
		public EffectsRenderTask( int position ) {
			super( position );
		}

	}
	
	
	class EffectsListAdapter extends ListAdapter {

		public EffectsListAdapter( Context context, int mainResId, int moreResId, int externalResId, int dividerResId, List<EffectPack> objects ) {
			super( context, mainResId, moreResId, externalResId, dividerResId, objects );
		}
		
		@Override
		protected Callable<Bitmap> createExternalContentCallable( String iconUrl ) {
			return new ExternalEffectsThumbnailCallable( iconUrl, 
					mCacheService, 
					mExternalFolderIcon, 
					getContext().getBaseContext().getResources(), 
					R.drawable.aviary_ic_na );
		}
		
		@Override
		protected Callable<Bitmap> createContentCallable( EffectPack item, int position, String effectName ) {
			return new FilterThumbnailCallable( item.getItemIdAt( position ), effectName, mThumbBitmap, item.mStatus == PluginService.ERROR_NONE, updateArrowBitmap );
		}
		
		@Override
		protected BitmapDrawable getExternalBackgroundDrawable( Context context ) {
			return (BitmapDrawable) context.getResources().getDrawable( R.drawable.aviary_effects_pack_background );
		}
	}
	

	class FilterThumbnailCallable implements Callable<Bitmap> {

		INativeFilter mFilter;
		String mEffectName;
		Bitmap srcBitmap;
		Bitmap invalidBitmap;
		boolean isValid;
		long mEffectId;

		public FilterThumbnailCallable( long effectId, String effectName, Bitmap bitmap, boolean valid, Bitmap invalid_bitmap ) {
			mEffectName = effectName;
			mEffectId = effectId;
			srcBitmap = bitmap;
			isValid = valid;
			invalidBitmap = invalid_bitmap;
		}
		
		INativeFilter loadFilter( long effectId, CharSequence effectName ) {
			EffectFilter filter = (EffectFilter) FilterLoaderFactory.get( Filters.EFFECTS );
			
			if( null != mCDSService ) {
				if( !mCDSService.opened() ) mCDSService.open();
				byte[] content = mCDSService.loadEntryContent( effectId );
				if( null != content ) {
					String contentString = new String( content );
					filter.setMoaLiteEffectContent( contentString );
				}
			}
			filter.setEffectName( effectName );
			return filter;			
		}

		@Override
		public Bitmap call() throws Exception {

			if ( null == mFilter ) {
				try {
					mFilter = loadFilter( mEffectId, mEffectName );
				} catch( Throwable t ) {
					t.printStackTrace();
					isValid = false;
				}
			}

			MoaActionList actionList = actionsForRoundedThumbnail( isValid, mFilter );
			MoaResult moaresult = NativeFilterProxy.prepareActions( actionList, srcBitmap, null, 1, 1 );
			moaresult.execute();
			Bitmap result = moaresult.outputBitmap;
			
			// Bitmap result = mFilter.execute( srcBitmap, null, 1, 1 );

			if ( !isValid ) {
				addUpdateArrow( result );
			}
			return result;
		}

		void addUpdateArrow( Bitmap bitmap ) {

			if ( null != invalidBitmap && !invalidBitmap.isRecycled() ) {

				final double w = Math.floor( bitmap.getWidth() * 0.75 );
				final double h = Math.floor( bitmap.getHeight() * 0.75 );

				final int paddingx = (int) ( bitmap.getWidth() - w ) / 2;
				final int paddingy = (int) ( bitmap.getHeight() - h ) / 2;

				Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG );
				Rect src = new Rect( 0, 0, invalidBitmap.getWidth(), invalidBitmap.getHeight() );
				Rect dst = new Rect( paddingx, paddingy, paddingx + (int) w, paddingy + (int) h );
				Canvas canvas = new Canvas( bitmap );
				canvas.drawBitmap( invalidBitmap, src, dst, paint );
			}
		}
		
		MoaActionList actionsForRoundedThumbnail( final boolean isValid, INativeFilter filter ) {
			
			MoaActionList actions = MoaActionFactory.actionList();
			if ( null != filter ) {
				actions.addAll( filter.getActions() );
			}

			MoaAction action = MoaActionFactory.action( "ext-roundedborders" );
			action.setValue( "padding", mThumbPadding );
			action.setValue( "roundPx", mThumbRoundedCorners );
			action.setValue( "strokeColor", mThumbStrokeColor );
			action.setValue( "strokeWeight", mThumbStrokeWidth );

			if ( !isValid ) {
				action.setValue( "overlaycolor", 0x99000000 );
			}
			
			actions.add( action );
			return actions;
		}		
	}
	
	static class ExternalEffectsThumbnailCallable extends ExternalFramesThumbnailCallable {
		
		public ExternalEffectsThumbnailCallable( String uri, ImageCacheService cacheService, BitmapDrawable folderBackground, Resources resources, int fallbackResId ) {
			super( uri, cacheService, folderBackground, resources, fallbackResId );
		}

		@SuppressWarnings("deprecation")
		@Override
		Bitmap generateBitmap( Bitmap icon ) {
			return BitmapUtils.flattenDrawables( mFolder, new BitmapDrawable( icon ), 1.0f, 0f );
		}
	}
}
