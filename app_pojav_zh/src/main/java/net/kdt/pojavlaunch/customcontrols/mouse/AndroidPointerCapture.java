package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.Tools;

import org.lwjgl.glfw.CallbackBridge;

public class AndroidPointerCapture implements ViewTreeObserver.OnWindowFocusChangeListener, View.OnCapturedPointerListener {
    private static final float TOUCHPAD_SCROLL_THRESHOLD = 1;
    private final AbstractTouchpad mTouchpad;
    private final View mHostView;
    private final float mScaleFactor;
    private final float mMousePrescale = Tools.dpToPx(1);
    private final Scroller mScroller = new Scroller(TOUCHPAD_SCROLL_THRESHOLD);

    // Adicione um atributo para verificar se um mouse OTG está conectado
    private boolean hasOTGMouse = false;

    public AndroidPointerCapture(AbstractTouchpad touchpad, View hostView, float scaleFactor) {
        this.mScaleFactor = scaleFactor;
        this.mTouchpad = touchpad;
        this.mHostView = hostView;
        hostView.setOnCapturedPointerListener(this);
        hostView.getViewTreeObserver().addOnWindowFocusChangeListener(this);

        // Verifique se um mouse OTG está conectado ao iniciar
        // Você pode usar a biblioteca "USB Host" ou outra biblioteca de acesso a hardware
        // para verificar se há um dispositivo USB conectado e identificar se é um mouse.
        hasOTGMouse = checkOTGMouseConnection(); // Implementar a verificação de conexão OTG
    }

    // Método para verificar a conexão do mouse OTG
    private boolean checkOTGMouseConnection() {
        // Implementar a lógica para verificar se há um mouse OTG conectado
        // Você precisará de uma biblioteca para interagir com o hardware USB, 
        // como "USB Host". 
        // Aqui está um exemplo simples, mas pode não funcionar sem a biblioteca correta:
        // return MainActivity.getUsbManager().getDeviceList().containsKey("USB_MOUSE");
        return false; // Substitua por sua lógica de detecção de OTG
    }

    private void enableTouchpadIfNecessary() {
        if (!mTouchpad.getDisplayState()) mTouchpad.enable(true);
    }

    public void handleAutomaticCapture() {
        if (!CallbackBridge.isGrabbing()) return;
        if (mHostView.hasPointerCapture()) {
            enableTouchpadIfNecessary();
            return;
        }
        if (!mHostView.hasWindowFocus()) {
            mHostView.requestFocus();
        } else {
            mHostView.requestPointerCapture();
        }
    }

    @Override
    public boolean onCapturedPointer(View view, MotionEvent event) {
        // Verifique se um mouse OTG está conectado
        if (hasOTGMouse) {
            // Pegue os eixos diretamente do evento
            float relX = event.getX() - event.getRawX();
            float relY = event.getY() - event.getRawY();

            // O mouse OTG não tem scroll, então o scroller não é necessário
            // Use o relX e relY diretamente para o CallbackBridge
            CallbackBridge.mouseX += (relX * mScaleFactor);
            CallbackBridge.mouseY += (relY * mScaleFactor);
            CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);

            // Envie os eventos de clique do mouse
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    CallbackBridge.sendMouseButton(0, true); // Clique esquerdo
                    return true;
                case MotionEvent.ACTION_UP:
                    CallbackBridge.sendMouseButton(0, false); // Clique esquerdo
                    return true;
                case MotionEvent.ACTION_MOVE:
                    return true; // Continue transmitindo os eventos de movimento do mouse
            }
        } else {
            // Caso contrário, use o código original para lidar com o touchpad
            float relX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X);
            float relY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y);
            if (!CallbackBridge.isGrabbing()) {
                enableTouchpadIfNecessary();
                // Yes, if the user's touchpad is multi-touch we will also receive events for that.
                // So, handle the scrolling gesture ourselves.
                relX *= mMousePrescale;
                relY *= mMousePrescale;
                if (event.getPointerCount() < 2) {
                    mTouchpad.applyMotionVector(relX, relY);
                    mScroller.resetScrollOvershoot();
                } else {
                    mScroller.performScroll(relX, relY);
                }
            } else {
                // Position is updated by many events, hence it is send regardless of the event value
                CallbackBridge.mouseX += (relX * mScaleFactor);
                CallbackBridge.mouseY += (relY * mScaleFactor);
                CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    return true;
                case MotionEvent.ACTION_BUTTON_PRESS:
                    return MinecraftGLSurface.sendMouseButtonUnconverted(event.getActionButton(), true);
                case MotionEvent.ACTION_BUTTON_RELEASE:
                    return MinecraftGLSurface.sendMouseButtonUnconverted(event.getActionButton(), false);
                case MotionEvent.ACTION_SCROLL:
                    CallbackBridge.sendScroll(
                            event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                            event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    );
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && MainActivity.isAndroid8OrHigher()) mHostView.requestPointerCapture();
    }

    public void detach() {
        mHostView.setOnCapturedPointerListener(null);
        mHostView.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
    }
}
