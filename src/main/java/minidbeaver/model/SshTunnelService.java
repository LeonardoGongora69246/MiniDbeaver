package minidbeaver.model;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SshTunnelService {

    private Session session;
    private int puertoLocal;


     // Abre la sesión SSH
    public int abrir(ConexionConfig cfg) throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(cfg.getSshUsuario(), cfg.getSshHost(), cfg.getSshPuerto());
        session.setPassword(cfg.getSshPassword());

        // Para la tarea aceptamos la llave del servidor sin verificarla
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10_000);
        puertoLocal = session.setPortForwardingL(0, cfg.getDbHost(), ConexionConfig.PUERTO_MARIADB);

        return puertoLocal;
    }

    public boolean estaAbierto() {
        return session != null && session.isConnected();
    }

    public int getPuertoLocal() {
        return puertoLocal;
    }

    public void cerrar() {
        if (estaAbierto()) {
            session.disconnect();
        }
    }
}
