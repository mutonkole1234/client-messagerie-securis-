import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Fenêtre de discussion individuelle pour discuter avec un contact donné.
 * 
 * Permet d’envoyer des messages texte et d’afficher la conversation de façon simple.
 * Chaque fenêtre correspond à un seul utilisateur (contact).
 */
public class FenetreDiscussion extends JFrame {

    // Pseudo du contact avec qui on discute
    private final String contact;

    // Référence vers l’instance principale client qui gère la connexion réseau
    private final ClientGUISSL client;

    // Zone de texte affichant la conversation complète
    private JTextArea areaDiscussion;

    // Champ texte permettant à l'utilisateur de taper un message
    private JTextField txtMessage;

    // Bouton pour envoyer le message
    private JButton btnEnvoyer;

    /**
     * Constructeur qui initialise la fenêtre avec le pseudo du contact
     * et la référence vers le client principal pour envoyer les messages.
     * 
     * @param contact le pseudo du contact avec qui discuter
     * @param client l’instance du client global (ClientGUISSL)
     */
    public FenetreDiscussion(String contact, ClientGUISSL client) {
        super("Chat avec " + contact); // Titre de la fenêtre

        this.contact = contact;
        this.client = client;

        // Initialise la zone de texte de discussion (non modifiable par l’utilisateur)
        areaDiscussion = new JTextArea(15, 30);
        areaDiscussion.setEditable(false);

        // Ajoute un ascenseur vertical si le contenu dépasse la taille
        JScrollPane scrollPane = new JScrollPane(areaDiscussion);

        // Champ texte pour écrire un message (largeur 25 colonnes)
        txtMessage = new JTextField(25);

        // Bouton pour envoyer le message
        btnEnvoyer = new JButton("Envoyer");

        // Panel inférieur contenant le champ texte et le bouton
        JPanel panelBas = new JPanel(new FlowLayout());
        panelBas.add(txtMessage);
        panelBas.add(btnEnvoyer);

        // Organisation de la fenêtre avec BorderLayout :
        // - centre la zone de discussion
        // - bas le panel d’envoi
        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(panelBas, BorderLayout.SOUTH);

        // Ajoute un écouteur d'action au bouton "Envoyer"
        btnEnvoyer.addActionListener(e -> envoyerMessage());

        // Permet aussi d’envoyer en appuyant sur la touche Entrée dans txtMessage
        txtMessage.addActionListener(e -> envoyerMessage());

        // Ferme seulement cette fenêtre quand clic sur la croix,
        // sans arrêter le client ni l’application entière
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // pack adapte la taille de la fenêtre aux composants
        pack();

        // centre la fenêtre à l’écran
        setLocationRelativeTo(null);
    }

    /**
     * Méthode appelée pour envoyer le message tapé dans txtMessage.
     * 
     * Vérifie que le message n’est pas vide, puis transmet au client,
     * affiche le message dans la fenêtre, et vide la zone de saisie.
     */
    private void envoyerMessage() {
        String msg = txtMessage.getText().trim(); // supprime espaces inutiles
        if (!msg.isEmpty()) {
            // Envoi au serveur via le client principal (serveur diffuse ensuite)
            client.envoyerMessage(contact, msg);

            // Affiche le message "Vous : ..." dans la zone de discussion
            afficherMessage("Vous : " + msg);

            // Vide le champ de saisie pour le prochain message
            txtMessage.setText("");
        }
    }

    /**
     * Affiche un message dans la zone de discussion.
     * Utilise SwingUtilities.invokeLater pour être sûr d’exécuter
     * dans le bon thread Swing (event dispatch thread).
     * Positionne aussi le caret pour faire défiler automatiquement vers la fin.
     * 
     * @param msg texte à afficher dans la conversation
     */
    public void afficherMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            areaDiscussion.append(msg + "\n");
            // Fait défiler automatiquement vers la fin du texte pour voir le dernier message
            areaDiscussion.setCaretPosition(areaDiscussion.getDocument().getLength());
        });
    }
}
