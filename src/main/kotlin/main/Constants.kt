package main

// constants used for imprecise inequality checks
const val EPSILON_WEIGHTS = 0.000001
const val EPSILON_PRICING_PROBLEM = 0.000001
const val EPSILON_COLOR_INTERPOLATE = 0.0001

// colors used for plotting graphs
const val DEPOT_COLOR = 0x00ff00

const val PARTIALLY_ASSIGNED_COLOR = 0x000000
const val PARTIALLY_ASSIGNED_CUSTOMER_COLOR = PARTIALLY_ASSIGNED_COLOR
const val PARTIALLY_ASSIGNED_EDGE_COLOR = PARTIALLY_ASSIGNED_COLOR

const val COMPLETELY_ASSIGNED_COLOR = 0x0000ff
const val CUSTOMER_COLOR = COMPLETELY_ASSIGNED_COLOR
const val COMPLETELY_ASSIGNED_CUSTOMER_COLOR = COMPLETELY_ASSIGNED_COLOR
const val COMPLETELY_ASSIGNED_EDGE_COLOR = COMPLETELY_ASSIGNED_COLOR

const val UNASSIGNED_COLOR = 0xafafaf
const val UNASSIGNED_CUSTOMER_COLOR = UNASSIGNED_COLOR
const val EDGE_COLOR = UNASSIGNED_COLOR
const val UNASSIGNED_EDGE_COLOR = UNASSIGNED_COLOR

// default parameters for [geometryBasedCut]
const val GEOMETRY_CUT_SCALE = 0.5
const val GEOMETRY_CUT_DEGREE = 3