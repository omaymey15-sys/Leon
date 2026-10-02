package com.myschoolocr.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myschoolocr.app.ui.theme.AppFonts

/**
 * Bouton principal avec un léger effet d'enfoncement (scale) au clic — sensation plus
 * premium/tactile qu'un Button Material3 par défaut, sans dépendance externe.
 */
@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "premiumButtonScale"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.scale(scale)
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Carte "élevée" avec ombre douce et coins arrondis généreux, pour remplacer les Card
 * par défaut sur les écrans à fort impact visuel (accueil, détail).
 */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)
    ) {
        Column(content = content)
    }
}

/** Écran de chargement plein écran, animé, pour remplacer un CircularProgressIndicator nu. */
@Composable
fun LoadingScreen(message: String = "Chargement…", modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * État vide illustré (icône dans un badge coloré + titre + sous-titre), centré
 * horizontalement. N'utilise PAS fillMaxSize (crash si placé dans un item de LazyColumn) ;
 * pour un centrage vertical complet en dehors d'une liste, entoure l'appel d'un
 * Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center).
 */
@Composable
fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * Titre "de marque" en Poppins — à utiliser avec parcimonie, uniquement sur 2-3 éléments
 * d'identité visuelle (nom de l'app, écran de bienvenue), jamais pour de la donnée ou du
 * texte long : Poppins est moins pratique que Roboto/Inter pour ça.
 */
@Composable
fun BrandTitle(text: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall) {
    val context = LocalContext.current
    val poppins = remember { AppFonts.poppinsFamily(context) }
    Text(text, style = style.copy(fontFamily = poppins, fontWeight = FontWeight.Bold))
}
