package com.willfp.reforges.display

import com.willfp.eco.core.display.Display
import com.willfp.eco.core.display.DisplayModule
import com.willfp.eco.core.display.DisplayPriority
import com.willfp.eco.core.display.DisplayProperties
import com.willfp.eco.core.fast.FastItemStack
import com.willfp.eco.core.fast.fast
import com.willfp.eco.core.placeholder.context.placeholderContext
import com.willfp.eco.util.SkullUtils
import com.willfp.eco.util.StringUtils
import com.willfp.eco.util.formatEco
import com.willfp.eco.util.toJSON
import com.willfp.libreforge.ItemProvidedHolder
import com.willfp.libreforge.ProvidedHolder
import com.willfp.reforges.ReforgesPlugin
import com.willfp.reforges.reforges.ReforgeTargets
import com.willfp.reforges.util.reforge
import com.willfp.reforges.util.reforgeStone
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType

@Suppress("DEPRECATION")
class ReforgesDisplay(private val plugin: ReforgesPlugin) : DisplayModule(plugin, DisplayPriority.HIGH) {
    private val tempKey = plugin.namespacedKeyFactory.create("temp")

    override fun display(
        itemStack: ItemStack,
        player: Player?,
        props: DisplayProperties,
        vararg args: Any
    ) {
        val targets = ReforgeTargets.getForItem(itemStack)

        val fast = itemStack.fast()

        val stone = fast.persistentDataContainer.reforgeStone

        if (targets.isEmpty() && stone == null) {
            return
        }

        val fastItemStack = FastItemStack.wrap(itemStack)

        val lore = fastItemStack.lore

        val reforge = fast.persistentDataContainer.reforge

        val context = placeholderContext(
            player = player,
            item = itemStack
        )

        if (reforge == null && stone == null) {
            if (plugin.configYml.getBool("reforge.show-reforgable")) {
                if (props.inGui) {
                    return
                }

                val addLore: MutableList<String> = ArrayList()
                for (string in plugin.configYml.getFormattedStrings("reforge.reforgable-suffix")) {
                    addLore.add(Display.PREFIX + string)
                }
                lore.addAll(addLore)
            }
        }

        if (stone != null) {
            val meta = itemStack.itemMeta
            meta.setDisplayName(stone.config.getFormattedString("stone.name"))
            val stoneMeta = stone.stone.itemMeta
            if (stoneMeta is SkullMeta) {
                val stoneTexture = SkullUtils.getSkullTexture(stoneMeta)

                if (stoneTexture != null) {
                    SkullUtils.setSkullTexture(meta as SkullMeta, stoneTexture)
                }
            }

            itemStack.itemMeta = meta

            val stoneLore = stone.config.getStrings("stone.lore")
                .map { it.replace("%price%", if (player == null) "" else stone.stonePrice?.getDisplay(player) ?: "") }
                .formatEco(player)
                .map { "${Display.PREFIX}$it" }

            lore.addAll(0, stoneLore)
        }

        if (reforge != null) {
            if (plugin.configYml.getBool("reforge.display-in-lore")) {
                // 1) Construit proprement la section reforge (un seul Display.PREFIX par ligne)
                val header = plugin.configYml
                    .getFormattedStrings("reforge.reforged-prefix")
                    .map { it.replace("%reforge%", reforge.name) }
                    .map { "${Display.PREFIX}$it" }

                val desc = reforge.description
                    .formatEco(context)
                    .map { "${Display.PREFIX}$it" }

                val addLore = ArrayList<String>(header.size + desc.size).apply {
                    addAll(header)
                    addAll(desc)
                }

                // 2) Construit (optionnel) le bloc "conditions non remplies" à coller juste après la reforge
                val conditions: List<String> = if (player != null) {
                    val provided = ItemProvidedHolder(reforge, itemStack)
                    val lines = provided.getNotMetLines(player)
                    if (lines.isNotEmpty()) {
                        buildList {
                            add(Display.PREFIX) // séparateur visuel
                            addAll(lines.map { "${Display.PREFIX}$it" })
                        }
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // 3) Insertion juste avant les 3 dernières lignes (souvent la rareté)
                val keepBottom = 3
                val insertionIndex = (lore.size - keepBottom).coerceAtLeast(0)

                lore.addAll(insertionIndex, addLore)
                if (conditions.isNotEmpty()) {
                    lore.addAll(insertionIndex + addLore.size, conditions)
                }
            }

            if (plugin.configYml.getBool("reforge.display-in-name")) {
                val displayName = fastItemStack.displayNameComponent

                if (!fastItemStack.displayName.contains(reforge.name)) {
                    fastItemStack.persistentDataContainer.set(
                        tempKey,
                        PersistentDataType.STRING,
                        displayName.toJSON()
                    )

                    val newName = reforge.namePrefixComponent.append(displayName)

                    fastItemStack.setDisplayName(newName)
                }
            }

            // NOTE: Les conditions manquantes sont déjà gérées dans le bloc ci-dessus
            // pour qu'elles s'insèrent au bon endroit (juste après la reforge et avant la rareté).
            // Si on voulait conserver l'ancien comportement (à la toute fin), il suffirait
            // de déplacer ce bloc hors de "display-in-lore" et de l'ajouter à la fin.
        }

        fastItemStack.lore = lore
    }

    override fun revert(itemStack: ItemStack) {
        itemStack.reforge ?: return

        val fis = FastItemStack.wrap(itemStack)

        if (!plugin.configYml.getBool("reforge.display-in-name")) {
            return
        }

        if (fis.persistentDataContainer.has(tempKey, PersistentDataType.STRING)) {
            fis.setDisplayName(
                StringUtils.jsonToComponent(
                    fis.persistentDataContainer.get(
                        tempKey,
                        PersistentDataType.STRING
                    )
                )
            )

            fis.persistentDataContainer.remove(tempKey)
        }
    }
}
