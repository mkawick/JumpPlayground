using UnityEngine;
using UnityEngine.AI;

#if UNITY_EDITOR
using UnityEditor;
#endif

public class FocusEditorCameraEditor : MonoBehaviour
{
    [MenuItem("Tiny Wizard/Scene Creation Tools/Frame Selected in Scene View")] 
    public static void FocusSelected() {
#if UNITY_EDITOR
        Camera camera = SceneView.lastActiveSceneView.camera;
        NavMeshHit navMeshHit = new NavMeshHit();
        NavMesh.SamplePosition(Selection.activeTransform.position, out navMeshHit, float.MaxValue, NavMesh.AllAreas);
        Vector3 sampledPosition = navMeshHit.position;

        if (!Selection.activeTransform) {
            Debug.LogError("FocusEditorCameraEditor: No active selection");
            return;
        }
        if (!camera) {
            Debug.LogError("FocusEditorCameraEditor: No scene view camera found");
            return;
        }
        if (!navMeshHit.hit) {
            sampledPosition = Selection.activeTransform.position;
        } else {
            if ((navMeshHit.position - Selection.activeTransform.position).magnitude > 10.0f) {
                sampledPosition = Selection.activeTransform.position;
            }
        }

        // NOTE:    Work-around because I can't have a quaternion without a GameObject
        GameObject go = new GameObject(); 
        Transform t = go.transform;
        t.position = sampledPosition;
        t.rotation = Quaternion.Euler(new Vector3(55f, 45f, 0f));
        t.Translate(new Vector3(0f, 0f, -28f), Space.Self);
        SceneView.lastActiveSceneView.cameraSettings.fieldOfView = 30;
        SceneView.lastActiveSceneView.AlignViewToObject(t);
        DestroyImmediate(go);
#endif
    }
}
