package net.kdt.pojavlaunch.game.platform;

import android.view.Surface;

import net.kdt.pojavlaunch.game.platform.backend.DummyBackend;
import net.kdt.pojavlaunch.game.platform.backend.PlatformBackend;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.*;

public class PlatformSurfaceTest {
    private PlatformBackend backend;
    private Surface surface;

    @Before
    public void setUp() {
        Platform.destroySurface();
        backend = mock(PlatformBackend.class);
        Platform.setPlatformLibrary(backend);
        surface = mock(Surface.class);
        when(surface.isValid()).thenReturn(true);
    }

    @After
    public void tearDown() {
        Platform.destroySurface();
        Platform.setPlatformLibrary(new DummyBackend());
    }

    @Test
    public void destroyedSurfaceIsNotReplayedDuringGlfwInit() {
        Platform.updateSurface(surface);
        Platform.destroySurface();
        PlatformBackend glfw = mock(PlatformBackend.class);
        Platform.setPlatformLibrary(glfw);

        verify(backend).surfaceCreated(surface);
        verify(backend).surfaceDestroyed();
        verifyNoInteractions(glfw);
    }

    @Test
    public void invalidSurfaceIsNotPassedToNativeBackend() {
        when(surface.isValid()).thenReturn(false);
        Platform.updateSurface(surface);
        Platform.updateSurface(null);

        verifyNoInteractions(backend);
    }

    @Test
    public void surfaceInvalidatedBeforeBackendSwitchIsNotReplayed() {
        Platform.updateSurface(surface);
        when(surface.isValid()).thenReturn(false);
        PlatformBackend glfw = mock(PlatformBackend.class);
        Platform.setPlatformLibrary(glfw);

        verify(backend).surfaceDestroyed();
        verifyNoInteractions(glfw);
    }

    @Test
    public void liveSurfaceIsTransferredAfterOldBackendDetaches() {
        Platform.updateSurface(surface);
        PlatformBackend glfw = mock(PlatformBackend.class);
        Platform.setPlatformLibrary(glfw);

        InOrder order = inOrder(backend, glfw);
        order.verify(backend).surfaceCreated(surface);
        order.verify(backend).surfaceDestroyed();
        order.verify(glfw).surfaceCreated(surface);
    }

    @Test
    public void recreatedSurfaceIsDeliveredAfterDestruction() {
        Platform.updateSurface(surface);
        Platform.destroySurface();
        Surface replacement = mock(Surface.class);
        when(replacement.isValid()).thenReturn(true);
        Platform.updateSurface(replacement);

        InOrder order = inOrder(backend);
        order.verify(backend).surfaceCreated(surface);
        order.verify(backend).surfaceDestroyed();
        order.verify(backend).surfaceCreated(replacement);
    }

    @Test
    public void duplicateCallbacksDoNotAcquireOrReleaseTwice() {
        Platform.updateSurface(surface);
        Platform.updateSurface(surface);
        Platform.destroySurface();
        Platform.destroySurface();

        verify(backend, times(1)).surfaceCreated(surface);
        verify(backend, times(1)).surfaceDestroyed();
        verifyNoMoreInteractions(backend);
    }

    @Test
    public void replacingLiveSurfaceDetachesOldWindowFirst() {
        Platform.updateSurface(surface);
        Surface replacement = mock(Surface.class);
        when(replacement.isValid()).thenReturn(true);
        Platform.updateSurface(replacement);

        InOrder order = inOrder(backend);
        order.verify(backend).surfaceCreated(surface);
        order.verify(backend).surfaceDestroyed();
        order.verify(backend).surfaceCreated(replacement);
    }
}
