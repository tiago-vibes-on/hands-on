import Phaser from 'phaser'
import { useEffect, useRef } from 'react'

const COMBAT_HEIGHT = 330

function snapshotCombatant(combatant) {
  return {
    id: combatant.id,
    name: combatant.name,
    role: combatant.role,
    maxHealth: combatant.maxHealth,
    currentHealth: combatant.currentHealth,
    maxMana: combatant.maxMana,
    currentMana: combatant.currentMana,
    level: combatant.level,
    magicLevel: combatant.magicLevel,
    healthRecovery: combatant.healthRecovery,
    manaRecovery: combatant.manaRecovery,
    runes: combatant.runes,
    spells: combatant.spells,
    damage: combatant.damage,
    attackInterval: combatant.attackInterval,
    criticalChance: combatant.criticalChance,
    criticalDamageMultiplier: combatant.criticalDamageMultiplier,
    color: combatant.color,
    alive: combatant.alive,
  }
}

function CombatScene({ battle, onBattleChange, onCombatEvent }) {
  const hostRef = useRef(null)
  const callbacksRef = useRef({ onBattleChange, onCombatEvent })
  const initialBattleRef = useRef(battle)

  callbacksRef.current = { onBattleChange, onCombatEvent }
  if (initialBattleRef.current.encounterId !== battle.encounterId) {
    initialBattleRef.current = battle
  }

  useEffect(() => {
    const host = hostRef.current
    const initialBattle = initialBattleRef.current
    if (!host) {
      return undefined
    }

    class BattleScene extends Phaser.Scene {
      constructor() {
        super('battle')
        this.heroes = initialBattle.heroes.map((hero) => ({ ...hero }))
        this.creatures = initialBattle.creatures.map((creature) => ({ ...creature }))
        this.status = initialBattle.status
        this.resizeHandler = this.layout.bind(this)
      }

      create() {
        this.field = this.add.graphics()
        this.add.text(22, 20, 'DAWNWATCH PARTY', {
          color: '#dce4f5',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '10px',
          fontStyle: 'bold',
          letterSpacing: 1,
        })
        this.enemyLabel = this.add.text(0, 20, 'CREATURES', {
          color: '#dce4f5',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '10px',
          fontStyle: 'bold',
          letterSpacing: 1,
        }).setOrigin(1, 0)

        this.heroes.forEach((hero, index) => this.createCombatant(hero, index, 'heroes'))
        this.creatures.forEach((creature, index) => this.createCombatant(creature, index, 'creatures'))
        this.scale.on('resize', this.resizeHandler)
        this.layout()

        if (this.status === 'in-progress') {
          this.emitEvent('Combat started. Every combatant attacks on its own timer.')
          this.heroes.forEach((hero, index) => {
            this.scheduleAttack(hero, 480 + (index * 170))
            hero.spells?.forEach((spell, spellIndex) => this.scheduleSpell(hero, spell, 900 + (spellIndex * 450)))
            this.scheduleRecovery(hero)
          })
          this.creatures.forEach((creature, index) => this.scheduleAttack(creature, 760 + (index * 160)))
        }
      }

      createCombatant(combatant, index, side) {
        const resourceTextX = side === 'heroes' ? -39 : 47
        const resourceTextOrigin = side === 'heroes' ? 1 : 0
        const resourceValueX = side === 'heroes' ? -34 : 42
        const resourceValueOrigin = side === 'heroes' ? 0 : 1
        const container = this.add.container(0, 0)
        const body = this.add.circle(0, 0, 26, combatant.color, 1)
        const ring = this.add.circle(0, 0, 26).setStrokeStyle(2, 0xf3e9ca, 0.55)
        const initial = this.add.text(0, 0, combatant.name.slice(0, 1), {
          color: '#fff8e7',
          fontFamily: 'Georgia, serif',
          fontSize: '20px',
          fontStyle: 'bold',
        }).setOrigin(0.5)
        const name = this.add.text(0, 44, combatant.name, {
          color: '#e6e9f0',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '11px',
          fontStyle: 'bold',
        }).setOrigin(0.5)
        const roleLabel = combatant.magicLevel ? `${combatant.role} · ML ${combatant.magicLevel}` : combatant.role ?? 'Creature'
        const role = this.add.text(0, 59, roleLabel, {
          color: '#94a0b9',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '9px',
        }).setOrigin(0.5)
        const healthTrack = this.add.rectangle(4, 74, 78, 8, 0x090e19, 1).setStrokeStyle(1, 0x56607a, 0.6)
        const healthValue = this.add.rectangle(resourceValueX, 74, 76, 6, 0x70bf8b, 1).setOrigin(resourceValueOrigin, 0.5)
        const healthText = this.add.text(resourceTextX, 74, '', {
          color: '#b9c4d8',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '9px',
          fontStyle: 'bold',
        }).setOrigin(resourceTextOrigin, 0.5)
        const elements = [body, ring, initial, name, role, healthTrack, healthValue, healthText]

        if (combatant.level) {
          const levelBadge = this.add.circle(-23, -23, 11, 0x283b61, 1).setStrokeStyle(1, 0xd9b560, 0.9)
          const levelText = this.add.text(-23, -23, combatant.level, {
            color: '#f7e5ae',
            fontFamily: 'system-ui, sans-serif',
            fontSize: '10px',
            fontStyle: 'bold',
          }).setOrigin(0.5)
          elements.push(levelBadge, levelText)
        }

        if (combatant.maxMana) {
          const manaTrack = this.add.rectangle(4, 88, 78, 8, 0x090e19, 1).setStrokeStyle(1, 0x56607a, 0.6)
          const manaValue = this.add.rectangle(resourceValueX, 88, 76, 6, 0x5d92df, 1).setOrigin(resourceValueOrigin, 0.5)
          const manaText = this.add.text(resourceTextX, 88, '', {
            color: '#b9c4d8',
            fontFamily: 'system-ui, sans-serif',
            fontSize: '9px',
            fontStyle: 'bold',
          }).setOrigin(resourceTextOrigin, 0.5)
          combatant.view = { manaText, manaValue }
          elements.push(manaTrack, manaValue, manaText)
        }

        if (combatant.spells?.length) {
          const spellSlots = combatant.spells.map((spell, spellIndex) => {
            const x = -13 + (spellIndex * 26)
            const slot = this.add.rectangle(x, 104, 22, 22, 0x2a2542, 1).setStrokeStyle(1, 0x8a70bd, 0.95)
            const symbol = this.add.text(x, 104, spell.symbol, {
              color: '#f1dc9c',
              fontFamily: 'system-ui, sans-serif',
              fontSize: '14px',
              fontStyle: 'bold',
            }).setOrigin(0.5)
            const cooldownOverlay = this.add.graphics()
            cooldownOverlay.setPosition(x, 104).setVisible(false)
            elements.push(slot, symbol, cooldownOverlay)
            return { spellId: spell.id, slot, symbol, cooldownOverlay, cooldownState: { remaining: 0 } }
          })
          combatant.view = { ...combatant.view, spellSlots }
        }

        if (combatant.role) {
          const equippedRunes = combatant.runes ?? []
          equippedRunes.forEach((rune, runeIndex) => {
            const x = -32 + (runeIndex * 16)
            const slot = this.add.rectangle(x, 128, 14, 14, rune ? 0x2b2b36 : 0x121a2b, 1).setStrokeStyle(1, rune ? 0xdcb662 : 0x59667e, 0.9)
            elements.push(slot)
            if (rune) {
              const symbol = this.add.text(x, 128, rune.symbol, {
                color: '#f2dfab',
                fontFamily: 'system-ui, sans-serif',
                fontSize: '9px',
                fontStyle: 'bold',
              }).setOrigin(0.5)
              elements.push(symbol)
            }
          })
        }

        container.add(elements)
        combatant.view = { ...combatant.view, container, healthValue, healthText, side, index, nextDamageLane: 0 }
        this.refreshCombatant(combatant)
      }

      layout() {
        const { width, height } = this.scale
        this.field.clear()
        this.field.fillStyle(0x121b2e, 1)
        this.field.fillRect(0, 0, width, height)
        this.field.fillStyle(0x16233a, 1)
        this.field.fillRect(14, 53, width - 28, height - 68)
        this.field.lineStyle(1, 0x33425e, 0.7)
        this.field.strokeRect(14, 53, width - 28, height - 68)
        this.field.lineBetween(width / 2, 67, width / 2, height - 28)
        this.enemyLabel.setX(width - 22)

        this.positionSide(this.heroes, 'heroes', width, height)
        this.positionSide(this.creatures, 'creatures', width, height)
      }

      positionSide(combatants, side, width, height) {
        const padding = Math.min(78, Math.max(48, width * 0.1))
        const left = side === 'heroes' ? padding : width * 0.58
        const right = side === 'heroes' ? width * 0.42 : width - padding
        const spacing = combatants.length === 1 ? 0 : (right - left) / (combatants.length - 1)

        combatants.forEach((combatant, index) => {
          const formationIndex = side === 'heroes' ? combatants.length - 1 - index : index
          combatant.view.homeX = combatants.length === 1 ? (left + right) / 2 : left + (formationIndex * spacing)
          combatant.view.homeY = height * 0.5
          combatant.view.container.setPosition(combatant.view.homeX, combatant.view.homeY)
        })
      }

      scheduleAttack(attacker, delay) {
        this.time.delayedCall(delay, () => {
          if (this.status !== 'in-progress' || !attacker.alive) {
            return
          }

          const targets = attacker.view.side === 'heroes' ? this.creatures : this.heroes
          const target = targets.find((combatant) => combatant.alive)
          if (!target) {
            return
          }

          this.performAttack(attacker, target)
          if (this.status === 'in-progress') {
            this.scheduleAttack(attacker, attacker.attackInterval)
          }
        })
      }

      scheduleRecovery(combatant) {
        if (!combatant.healthRecovery && !combatant.manaRecovery) {
          return
        }

        this.time.delayedCall(1000, () => {
          if (this.status !== 'in-progress' || !combatant.alive) {
            return
          }

          const nextHealth = Math.min(combatant.maxHealth, combatant.currentHealth + combatant.healthRecovery)
          const nextMana = Math.min(combatant.maxMana, combatant.currentMana + combatant.manaRecovery)
          if (nextHealth !== combatant.currentHealth || nextMana !== combatant.currentMana) {
            combatant.currentHealth = nextHealth
            combatant.currentMana = nextMana
            this.refreshCombatant(combatant)
            this.publishState()
          }
          this.scheduleRecovery(combatant)
        })
      }

      scheduleSpell(caster, spell, delay) {
        this.time.delayedCall(delay, () => {
          if (this.status !== 'in-progress' || !caster.alive || caster.magicLevel < spell.requiredMagicLevel) {
            return
          }

          const spellSlot = caster.view.spellSlots?.find((entry) => entry.spellId === spell.id)
          spellSlot?.slot.setAlpha(1)
          spellSlot?.symbol.setAlpha(1)
          if (caster.currentMana < spell.manaCost) {
            spellSlot?.slot.setAlpha(0.35)
            spellSlot?.symbol.setAlpha(0.35)
            this.clearSpellCooldown(spellSlot)
            this.scheduleSpell(caster, spell, 1000)
            return
          }

          const opponents = this.creatures.filter((creature) => creature.alive)
          const targets = spell.target === 'All targets' ? opponents : opponents.slice(0, 1)
          if (targets.length === 0) {
            return
          }

          const damage = Math.round(spell.baseDamage + (caster.magicLevel * spell.magicLevelScaling))
          caster.currentMana -= spell.manaCost
          this.refreshCombatant(caster)
          spellSlot?.slot.setAlpha(0.45)
          spellSlot?.symbol.setAlpha(0.45)
          this.playSpellCooldown(spellSlot, spell.cooldown)
          this.showSpell(caster, spell.symbol)
          const hits = targets.map((target) => this.resolveDamage(target, damage, 'magic', caster.criticalChance, caster.criticalDamageMultiplier))
          const criticalHits = hits.filter((hit) => hit.isCritical).length
          this.emitEvent(`${caster.name} casts ${spell.name} for ${damage} damage${targets.length > 1 ? ' to all targets' : ''}${criticalHits ? ` (${criticalHits} critical hit${criticalHits === 1 ? '' : 's'})` : ''}.`)
          this.finishIfNeeded()
          this.publishState()

          if (this.status === 'in-progress') {
            this.scheduleSpell(caster, spell, spell.cooldown)
          }
        })
      }

      playSpellCooldown(spellSlot, cooldown) {
        if (!spellSlot) {
          return
        }

        this.tweens.killTweensOf(spellSlot.cooldownState)
        spellSlot.cooldownState.remaining = 1
        this.drawSpellCooldown(spellSlot.cooldownOverlay, 1)
        spellSlot.cooldownOverlay.setVisible(true)
        this.tweens.add({
          targets: spellSlot.cooldownState,
          remaining: 0,
          duration: cooldown,
          ease: 'Linear',
          onUpdate: () => this.drawSpellCooldown(spellSlot.cooldownOverlay, spellSlot.cooldownState.remaining),
          onComplete: () => spellSlot.cooldownOverlay.clear().setVisible(false),
        })
      }

      drawSpellCooldown(overlay, remaining) {
        overlay.clear()
        if (remaining <= 0) {
          return
        }

        const startAngle = -Math.PI / 2
        const endAngle = startAngle - (Math.min(remaining, 0.9999) * Math.PI * 2)
        overlay.fillStyle(0x080d18, 0.76)
        overlay.beginPath()
        overlay.moveTo(0, 0)
        overlay.arc(0, 0, 10, startAngle, endAngle, true)
        overlay.closePath()
        overlay.fillPath()
      }

      clearSpellCooldown(spellSlot) {
        if (!spellSlot) {
          return
        }

        this.tweens.killTweensOf(spellSlot.cooldownState)
        spellSlot.cooldownOverlay.clear().setVisible(false)
      }

      performAttack(attacker, target) {
        const direction = attacker.view.side === 'heroes' ? 1 : -1

        this.tweens.add({
          targets: attacker.view.container,
          x: attacker.view.homeX + (18 * direction),
          yoyo: true,
          duration: 120,
        })
        const hit = this.resolveDamage(target, attacker.damage, 'basic', attacker.criticalChance, attacker.criticalDamageMultiplier)
        this.emitEvent(`${attacker.name}${hit.isCritical ? ' critically hits' : ' hits'} ${target.name} for ${hit.damage}.`)
        this.finishIfNeeded()
        this.publishState()
      }

      resolveDamage(target, baseDamage, damageType, criticalChance, criticalDamageMultiplier) {
        const isCritical = Math.random() < criticalChance
        const damage = isCritical ? Math.round(baseDamage * criticalDamageMultiplier) : baseDamage
        this.applyDamage(target, damage, damageType, isCritical)
        return { damage, isCritical }
      }

      applyDamage(target, damage, damageType, isCritical) {
        target.currentHealth = Math.max(0, target.currentHealth - damage)
        this.tweens.add({
          targets: target.view.container,
          alpha: 0.35,
          yoyo: true,
          duration: 110,
        })
        if (isCritical) {
          this.showCriticalImpact(target)
        }
        this.showDamage(target, damage, damageType, isCritical)
        this.refreshCombatant(target)

        if (target.currentHealth === 0) {
          target.alive = false
          target.view.container.setAlpha(0.32)
          this.emitEvent(`${target.name} is defeated.`)
        }
      }

      showSpell(caster, symbol) {
        const text = this.add.text(caster.view.homeX, caster.view.homeY - 48, symbol, {
          color: '#d9b9ff',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '22px',
          fontStyle: 'bold',
        }).setOrigin(0.5)

        this.tweens.add({
          targets: text,
          y: text.y - 18,
          alpha: 0,
          duration: 650,
          onComplete: () => text.destroy(),
        })
      }

      showDamage(target, damage, damageType, isCritical) {
        const lanes = [-13, 0, 13]
        const laneIndex = target.view.nextDamageLane
        const laneOffset = lanes[laneIndex]
        target.view.nextDamageLane = (laneIndex + 1) % lanes.length
        const startX = target.view.homeX + laneOffset
        const startY = target.view.homeY - 43 - (laneIndex === 1 ? 5 : 0)
        const text = this.add.text(startX, startY, `-${damage}`, {
          color: isCritical ? '#fff0a8' : damageType === 'magic' ? '#d9b9ff' : '#f4ce78',
          fontFamily: 'system-ui, sans-serif',
          fontSize: isCritical ? '20px' : '16px',
          fontStyle: 'bold',
        }).setOrigin(0.5).setStroke(isCritical ? '#a85536' : '#080d18', isCritical ? 4 : 3)

        this.tweens.add({
          targets: text,
          x: startX + (laneOffset * 0.7),
          y: startY - 24,
          alpha: 0,
          duration: 650,
          onComplete: () => text.destroy(),
        })
      }

      showCriticalImpact(target) {
        const impact = this.add.text(target.view.homeX, target.view.homeY - 4, '✦', {
          color: '#fff0a8',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '40px',
          fontStyle: 'bold',
        }).setOrigin(0.5).setStroke('#a85536', 3)

        this.tweens.add({
          targets: target.view.container,
          x: target.view.homeX + 5,
          yoyo: true,
          repeat: 3,
          duration: 40,
          onComplete: () => target.view.container.setX(target.view.homeX),
        })
        this.tweens.add({
          targets: impact,
          scale: 1.5,
          alpha: 0,
          duration: 320,
          onComplete: () => impact.destroy(),
        })
      }

      refreshCombatant(combatant) {
        const percentage = combatant.currentHealth / combatant.maxHealth
        combatant.view.healthValue.displayWidth = 76 * percentage
        combatant.view.healthValue.setFillStyle(percentage > 0.35 ? 0x70bf8b : 0xd99462)
        combatant.view.healthText.setText(combatant.currentHealth)
        if (combatant.view.manaValue) {
          const manaPercentage = combatant.currentMana / combatant.maxMana
          combatant.view.manaValue.displayWidth = 76 * manaPercentage
          combatant.view.manaText.setText(combatant.currentMana)
        }
      }

      finishIfNeeded() {
        const heroesAlive = this.heroes.some((hero) => hero.alive)
        const creaturesAlive = this.creatures.some((creature) => creature.alive)
        if (heroesAlive && creaturesAlive) {
          return
        }

        this.status = heroesAlive ? 'victory' : 'defeat'
        this.emitEvent(heroesAlive ? 'Encounter cleared. The party can continue the quest.' : 'The party has been defeated.')
      }

      emitEvent(message) {
        callbacksRef.current.onCombatEvent(message)
      }

      publishState() {
        callbacksRef.current.onBattleChange({
          encounterId: initialBattle.encounterId,
          status: this.status,
          heroes: this.heroes.map(snapshotCombatant),
          creatures: this.creatures.map(snapshotCombatant),
        })
      }

      shutdown() {
        this.scale.off('resize', this.resizeHandler)
      }
    }

    const game = new Phaser.Game({
      type: Phaser.AUTO,
      parent: host,
      width: Math.max(host.clientWidth, 320),
      height: COMBAT_HEIGHT,
      backgroundColor: '#121b2e',
      scene: BattleScene,
    })
    const resizeObserver = new ResizeObserver(() => {
      game.scale.resize(Math.max(host.clientWidth, 320), COMBAT_HEIGHT)
    })

    resizeObserver.observe(host)
    return () => {
      resizeObserver.disconnect()
      game.destroy(true)
    }
  }, [battle.encounterId])

  return <div className="combat-scene" ref={hostRef} aria-label="Automatic battle in progress" />
}

export default CombatScene
