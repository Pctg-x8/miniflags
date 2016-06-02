package com.cterm2.miniflags.common

// Enumerated Color
package object EnumColor
{
	sealed abstract class Type(val value: Int, val r: Float, val g: Float, val b: Float)
	case object White extends		Type( 0, 1.0f, 1.0f, 1.0f)
	case object Orange extends		Type( 1, 1.0f, 0.5f, 0.0f)
	case object Magenta extends		Type( 2, 1.0f, 0.0f, 0.5f)
	case object LightBlue extends	Type( 3, 0.5f, 0.5f, 1.0f)
	case object Yellow extends		Type( 4, 1.0f, 1.0f, 0.0f)
	case object Lime extends		Type( 5, 0.0f, 1.0f, 0.5f)
	case object Pink extends		Type( 6, 1.0f, 0.5f, 1.0f)
	case object Gray extends		Type( 7, 0.5f, 0.5f, 0.5f)
	case object Silver extends		Type( 8, 0.75f, 0.75f, 0.75f)
	case object Cyan extends		Type( 9, 0.0f, 0.5f, 1.0f)
	case object Purple extends		Type(10, 0.5f, 0.0f, 0.5f)
	case object Blue extends		Type(11, 0.0f, 0.0f, 1.0f)
	case object Brown extends		Type(12, 0.5f, 0.0f, 0.0f)
	case object Green extends		Type(13, 0.0f, 0.75f, 0.0f)
	case object Red extends			Type(14, 1.0f, 0.0f, 0.0f)
	case object Black extends		Type(15, 0.0f, 0.0f, 0.0f)

	def fromValue(value: Int) = (value & 0x0f) match
	{
		case 0 => White
		case 1 => Orange
		case 2 => Magenta
		case 3 => LightBlue
		case 4 => Yellow
		case 5 => Lime
		case 6 => Pink
		case 7 => Gray
		case 8 => Silver
		case 9 => Cyan
		case 10 => Purple
		case 11 => Blue
		case 12 => Brown
		case 13 => Green
		case 14 => Red
		case 15 => Black
	}
	def fromMeta(meta: Int) = fromValue(~meta & 0x0f)
}
