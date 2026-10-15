package net.kdt.pojavlaunch;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import net.kdt.pojavlaunch.R;

public class FatalErrorActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Bundle extras = getIntent().getExtras();
		if(extras == null) {
			finish();
			return;
		}
		boolean storageAllow = extras.getBoolean("storageAllow", false);
		String strSavePath = extras.getString("savePath");
		String preformatted = extras.getString("stackTrace");
		Throwable throwable = null;
		try {
			throwable = (Throwable) extras.getSerializable("throwable");
		} catch (Throwable ignored) {
		}
		final String stackTrace = resolveStackTrace(preformatted, throwable, strSavePath);
		String errHeader = storageAllow ?
			"Crash stack trace saved to " + strSavePath + "." :
			"Storage permission is required to save crash stack trace!";
		
		new AlertDialog.Builder(this)
			.setTitle(R.string.error_fatal)
			.setMessage(errHeader + "\n\n" + stackTrace)
			.setPositiveButton(android.R.string.ok, (p1, p2) -> finish())
			.setNegativeButton(R.string.global_restart, (p1, p2) -> startActivity(new Intent(FatalErrorActivity.this, LauncherActivity.class)))
			.setNeutralButton(android.R.string.copy, (p1, p2) -> {
				ClipboardManager mgr = (ClipboardManager) FatalErrorActivity.this.getSystemService(CLIPBOARD_SERVICE);
				mgr.setPrimaryClip(ClipData.newPlainText("error", stackTrace));

				finish();
			})
			.setCancelable(false)
			.show();
	}

	private static String resolveStackTrace(String preformatted, Throwable throwable, String savePath) {
		if (preformatted != null && !preformatted.trim().isEmpty()) {
			return preformatted;
		}
		if (throwable != null) {
			try {
				String printed = Tools.printToString(throwable);
				if (printed != null && !printed.trim().isEmpty()) {
					return printed;
				}
			} catch (Throwable ignored) {
			}
			try {
				String fallback = safeFormatThrowable(throwable);
				if (!fallback.trim().isEmpty()) {
					return fallback;
				}
			} catch (Throwable ignored) {
			}
		}
		if (savePath != null) {
			try {
				File file = new File(savePath);
				if (file.isFile() && file.canRead()) {
					String saved = Tools.read(file.getAbsolutePath());
					if (saved != null && !saved.trim().isEmpty()) {
						return saved;
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return "<null>";
	}

	public static String safeFormatThrowable(Throwable root) {
		StringBuilder sb = new StringBuilder();
		Throwable current = root;
		int depth = 0;
		while (current != null && depth < 16) {
			if (depth > 0) sb.append("Caused by: ");
			try {
				sb.append(current.getClass().getName());
				String msg = current.getMessage();
				if (msg != null) sb.append(": ").append(msg);
			} catch (Throwable ignored) {
				sb.append(current.getClass().getName());
			}
			sb.append('\n');
			try {
				StackTraceElement[] frames = current.getStackTrace();
				if (frames != null) {
					for (StackTraceElement frame : frames) {
						if (frame != null) {
							sb.append("\tat ").append(frame).append('\n');
						}
					}
				}
			} catch (Throwable ignored) {
			}
			Throwable next = null;
			try {
				next = current.getCause();
			} catch (Throwable ignored) {
			}
			if (next == current) break;
			current = next;
			depth++;
		}
		return sb.toString();
	}

	public static void showError(Context ctx, String savePath, boolean storageAllow, Throwable th) {
		Intent fatalErrorIntent = new Intent(ctx, FatalErrorActivity.class);
		fatalErrorIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		fatalErrorIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		String formatted = null;
		if (th != null) {
			try {
				formatted = Log.getStackTraceString(th);
			} catch (Throwable ignored) {
			}
			if (formatted == null || formatted.trim().isEmpty()) {
				try {
					formatted = safeFormatThrowable(th);
				} catch (Throwable ignored) {
				}
			}
		}
		if (formatted != null) {
			fatalErrorIntent.putExtra("stackTrace", formatted);
		}
		fatalErrorIntent.putExtra("throwable", th);
		fatalErrorIntent.putExtra("savePath", savePath);
		fatalErrorIntent.putExtra("storageAllow", storageAllow);
		ctx.startActivity(fatalErrorIntent);
	}
}
