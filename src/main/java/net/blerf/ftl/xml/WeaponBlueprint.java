/*
 * This file comes from Vhati's FTL Profile Editor (GPL-2.0) and was modified for Federation Home Planet:
 * cooldown made a float (some weapons use decimals) and the extra combat stats read, for tooltips.
 * See CREDITS.md and LICENSE at the root of the project.
 */
package net.blerf.ftl.xml;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import net.blerf.ftl.xml.DefaultDeferredText;


@XmlRootElement( name = "weaponBlueprint" )
@XmlAccessorType( XmlAccessType.FIELD )
public class WeaponBlueprint {
	
	@XmlAttribute( name = "name" )
	private String id;

	private String type;
	private DefaultDeferredText title;

	@XmlElement( name = "short" )
	private DefaultDeferredText shortTitle;

	@XmlElement( required = false )
	private Integer locked;

	private DefaultDeferredText desc;
	private DefaultDeferredText tooltip;

	@XmlElement( name = "sp" )
	private int shieldPiercing;

	@XmlElement( name = "bp" )
	private int bp;  // TODO: Rename this.

	private int damage;
	private int shots;
	private int fireChance;
	private int breachChance;
	private float cooldown;  // seconds; some weapons use decimals (Chain Vulcan: 11.1)
	private int power;
	private int cost;
	private int rarity;

	@XmlElement( name = "image" )
	private String projectileAnimId;  // Projectile / Beam-spot anim.

	@XmlElementWrapper(name = "launchSounds")
	@XmlElement( name = "sound" )
	private List<String> launchSounds;

	@XmlElementWrapper( name = "hitShipSounds" )
	@XmlElement( name = "sound" )
	private List<String> hitShipSounds;

	@XmlElementWrapper( name = "hitShieldSounds" )
	@XmlElement( name = "sound" )
	private List<String> hitShieldSounds;

	@XmlElementWrapper( name = "missSounds" )
	@XmlElement( name = "sound" )
	private List<String> missSounds;

	@XmlElement( name = "weaponArt" )
	private String weaponAnimId;

	// Extra combat stats (optional in the XML). Added for Homeworld tooltips.
	@XmlElement( required = false ) private Integer missiles;
	@XmlElement( required = false ) private Integer ion;
	@XmlElement( required = false ) private Integer stunChance;
	@XmlElement( required = false ) private Integer stun;
	@XmlElement( required = false ) private Integer persDamage;
	@XmlElement( required = false ) private Integer sysDamage;
	@XmlElement( required = false ) private Integer hullBust;
	@XmlElement( required = false ) private Integer length;
	@XmlElement( required = false ) private Integer lockdown;
	@XmlElement( required = false ) private Integer chargeLevels;
	@XmlElement( required = false ) private Boost boost;

	/** Chain weapons: each volley improves cooldown or damage by amount, up to count times. */
	@XmlAccessorType( XmlAccessType.FIELD )
	public static class Boost {
		public String type;
		public float amount;
		public int count;
	}
	public Boost getBoost() { return boost; }

	public int getMissiles() { return missiles == null ? 0 : missiles; }
	public int getIonDamage() { return ion == null ? 0 : ion; }
	public int getStunChance() { return stunChance == null ? 0 : stunChance; }
	public int getStun() { return stun == null ? 0 : stun; }
	public int getPersDamage() { return persDamage == null ? 0 : persDamage; }
	public int getSysDamage() { return sysDamage == null ? 0 : sysDamage; }
	public boolean isHullBust() { return hullBust != null && hullBust != 0; }
	public int getLength() { return length == null ? 0 : length; }
	public boolean isLockdown() { return lockdown != null && lockdown != 0; }
	public int getChargeLevels() { return chargeLevels == null ? 0 : chargeLevels; }
	

	public void setId( String id ) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public void setType( String type ) {
		this.type = type;
	}

	public String getType() {
		return type;
	}

	public void setTitle( DefaultDeferredText title ) {
		this.title = title;
	}

	public DefaultDeferredText getTitle() {
		return title;
	}

	public void setShortTitle( DefaultDeferredText shortTitle ) {
		this.shortTitle = shortTitle;
	}

	public DefaultDeferredText getShortTitle() {
		return shortTitle;
	}

	public void setLocked( Integer locked ) {
		this.locked = locked;
	}

	public Integer getLocked() {
		return locked;
	}

	public void setDescription( DefaultDeferredText desc ) {
		this.desc = desc;
	}

	public DefaultDeferredText getDescription() {
		return desc;
	}

	public void setTooltip( DefaultDeferredText tooltip ) {
		this.tooltip = tooltip;
	}

	public DefaultDeferredText getTooltip() {
		return tooltip;
	}

	public void setShieldPiercing( int shieldPiercing ) {
		this.shieldPiercing = shieldPiercing;
	}

	public int getShieldPiercing() {
		return shieldPiercing;
	}

	public void setBP( int bp ) {
		this.bp = bp;
	}

	public int getBP() {
		return bp;
	}

	public void setDamage( int damage ) {
		this.damage = damage;
	}

	public int getDamage() {
		return damage;
	}

	public void setShots( int shots ) {
		this.shots = shots;
	}

	public int getShots() {
		return shots;
	}

	public void setFireChance( int fireChance ) {
		this.fireChance = fireChance;
	}

	public int getFireChance() {
		return fireChance;
	}

	public void setBreachChance( int breachChance ) {
		this.breachChance = breachChance;
	}

	public int getBreachChance() {
		return breachChance;
	}

	public void setCooldown( float cooldown ) {
		this.cooldown = cooldown;
	}

	public float getCooldown() {
		return cooldown;
	}

	public void setPower( int power ) {
		this.power = power;
	}

	public int getPower() {
		return power;
	}

	public void setCost( int cost ) {
		this.cost = cost;
	}

	public int getCost() {
		return cost;
	}

	public void setRarity( int rarity ) {
		this.rarity = rarity;
	}

	public int getRarity() {
		return cost;
	}

	public void setProjectileAnimId( String projectileAnimId ) {
		this.projectileAnimId = projectileAnimId;
	}

	public String getProjectileAnimId() {
		return projectileAnimId;
	}

	public void setLaunchSounds( List<String> launchSounds ) {
		this.launchSounds = launchSounds;
	}

	public List<String> getLaunchSounds() {
		return launchSounds;
	}

	public void setHitShipSounds( List<String> hitShipSounds ) {
		this.hitShipSounds = hitShipSounds;
	}

	public List<String> getHitShipSounds() {
		return hitShipSounds;
	}

	public void setHitShieldSounds( List<String> hitShieldSounds ) {
		this.hitShieldSounds = hitShieldSounds;
	}

	public List<String> getHitShieldSounds() {
		return hitShieldSounds;
	}

	public void setMissSounds( List<String> missSounds ) {
		this.missSounds = missSounds;
	}

	public List<String> getMissSounds() {
		return missSounds;
	}

	public void setWeaponAnimId( String weaponAnimId ) {
		this.weaponAnimId = weaponAnimId;
	}

	public String getWeaponAnimId() {
		return weaponAnimId;
	}

	@Override
	public String toString() {
		return ""+title;
	}
}
