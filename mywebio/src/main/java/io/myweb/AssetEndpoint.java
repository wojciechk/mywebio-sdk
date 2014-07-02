package io.myweb;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.LocalSocket;
import android.util.Log;
import io.myweb.api.MimeTypes;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class AssetEndpoint extends Endpoint {

	private static final Map<String, Integer> EMPTY_MAP = new HashMap<String, Integer>(0);

	public AssetEndpoint(Context context) {
		super(context);
	}

	@Override
	protected String httpMethod() {
		return "GET";
	}

	@Override
	protected String originalPath() {
		return "/";
	}

	@Override
	protected Pattern getPattern() {
		return null;
	}

	@Override
	public boolean match(String method, String uri) {
		Log.d("AssetEndpoint", "trying to match: " + uri);
		if ("GET".equals(method)) {
			AssetManager assetManager = getContext().getAssets();
			try {
				assetManager.open("webio" + uri).close();
				Log.d("AssetEndpoint", "matched: " + uri);
				return true;
			} catch (IOException e) {
				Log.d("AssetEndpoint", "not matched: " + uri + " (" + e + ")");
				return false;
			}
		}
		return false;
	}

	@Override
	public void invoke(String uri, String request, LocalSocket localSocket, String reqId) {
		AssetManager assetManager = getContext().getAssets();
		try {
			String contentType = MimeTypes.getMimeType(uri);
			OutputStream os = outputStream(localSocket);
			writeResponseHeaders(os, reqId);
			InputStream is = assetManager.open("webio" + uri);
			ResponseBuilder responseBuilder = new ResponseBuilder();
			responseBuilder.writeResponse(contentType, is, os);
		} catch (IOException e) {
			Log.e("AssetEndpoint", "error during invoke", e);
		}
	}

}
